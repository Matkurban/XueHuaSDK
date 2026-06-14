package com.kurban.xuehuaim.sdk.sync

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.enum.ConversationType
import com.kurban.xuehuaim.sdk.event.ConversationEvent
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.model.ConversationInfo
import com.kurban.xuehuaim.sdk.network.http.ImApiService
import com.kurban.xuehuaim.sdk.util.SdkLogger
import com.kurban.xuehuaim.sdk.util.normalizedForStorage

internal object ConversationSync {
    private val log = SdkLogger.tag("ConversationSync")
    private const val VERSION_TABLE = "conversations"

    suspend fun syncFromServer(
        apiService: ImApiService,
        databaseService: DatabaseService,
        eventEmitter: SdkEventEmitter,
        userId: String,
    ): Int {
        val synced =
            syncFromServer(apiService as ConversationSyncApi, databaseService, eventEmitter, userId)
        ConversationDisplayEnricher.enrichConversationDisplayNames(
            apiService = apiService,
            databaseService = databaseService,
            eventEmitter = eventEmitter,
            userId = userId,
        )
        return synced
    }

    suspend fun syncFromServer(
        apiService: ConversationSyncApi,
        databaseService: DatabaseService,
        eventEmitter: SdkEventEmitter,
        userId: String,
    ): Int {
        val versionInfo = databaseService.getVersionSync(VERSION_TABLE, userId)
        val localVersion = versionInfo?.version ?: 0
        val localVersionId = versionInfo?.versionID.orEmpty()

        val synced = runCatching {
            syncIncremental(
                apiService,
                databaseService,
                eventEmitter,
                userId,
                localVersion,
                localVersionId
            )
        }.getOrElse { error ->
            log.error(error) { "incremental conversation sync failed, falling back to full sync" }
            syncFull(apiService, databaseService, userId)
        }

        applyServerSeqs(apiService, databaseService, eventEmitter, userId)
        emitTotalUnread(databaseService, eventEmitter)
        return synced
    }

    suspend fun applyServerSeqs(
        apiService: ImApiService,
        databaseService: DatabaseService,
        eventEmitter: SdkEventEmitter,
        userId: String,
    ) = applyServerSeqs(apiService as ConversationSyncApi, databaseService, eventEmitter, userId)

    suspend fun applyServerSeqs(
        apiService: ConversationSyncApi,
        databaseService: DatabaseService,
        eventEmitter: SdkEventEmitter,
        userId: String,
    ) {
        val seqResp = runCatching {
            apiService.getConversationsHasReadAndMaxSeq(userId, emptyList())
        }.getOrElse { error ->
            log.error(error) { "getConversationsHasReadAndMaxSeq failed" }
            return
        }
        val localConversations = databaseService.getAllConversations()
            .associateBy { it.conversationID }
        val missingConversationIds = mutableListOf<String>()
        val changedConversationIds = mutableListOf<String>()

        seqResp.seqs.forEach { (conversationId, seqInfo) ->
            val unreadCount = MessageSeqSync.unreadCountFromSeq(seqInfo.maxSeq, seqInfo.hasReadSeq)
            val existing = localConversations[conversationId]
            if (existing == null) {
                missingConversationIds += conversationId
                return@forEach
            }
            val effectiveHasReadSeq = maxOf(existing.hasReadSeq, seqInfo.hasReadSeq)
            val effectiveMinSeq = when {
                seqInfo.minSeq > 0 -> maxOf(existing.minSeq, seqInfo.minSeq)
                else -> existing.minSeq
            }
            val updated = existing.copy(
                maxSeq = seqInfo.maxSeq,
                hasReadSeq = effectiveHasReadSeq,
                minSeq = effectiveMinSeq,
                unreadCount = unreadCount,
            )
            if (updated != existing) {
                databaseService.insertOrReplaceConversation(updated.normalizedForStorage())
                changedConversationIds += conversationId
            }
        }

        if (missingConversationIds.isNotEmpty()) {
            runCatching {
                val remote = apiService.getConversations(userId, missingConversationIds)
                remote.forEach { conversation ->
                    if (shouldSkipConversation(conversation, userId)) return@forEach
                    val seqInfo = seqResp.seqs[conversation.conversationID] ?: return@forEach
                    val unreadCount = MessageSeqSync.unreadCountFromSeq(
                        seqInfo.maxSeq,
                        seqInfo.hasReadSeq,
                    )
                    val normalized = conversation.normalizedForStorage().copy(
                        maxSeq = seqInfo.maxSeq,
                        hasReadSeq = seqInfo.hasReadSeq,
                        minSeq = maxOf(conversation.minSeq, seqInfo.minSeq),
                        unreadCount = unreadCount,
                    )
                    databaseService.insertOrReplaceConversation(normalized)
                    changedConversationIds += conversation.conversationID
                }
            }.onFailure { error ->
                log.error(error) { "getConversations for missing ids failed" }
            }
        }

        changedConversationIds.distinct().forEach { conversationId ->
            databaseService.getConversation(conversationId)?.let { conv ->
                eventEmitter.emitConversation(ConversationEvent.Changed(conv))
            }
        }
        if (changedConversationIds.isNotEmpty()) {
            emitTotalUnread(databaseService, eventEmitter)
        }
    }

    private suspend fun syncIncremental(
        apiService: ConversationSyncApi,
        databaseService: DatabaseService,
        eventEmitter: SdkEventEmitter,
        userId: String,
        localVersion: Int,
        localVersionId: String,
    ): Int {
        val resp = apiService.getIncrementalConversations(userId, localVersion, localVersionId)
        if (resp.full) {
            log.info {
                "conversation full sync (full=${resp.full}, localVersion=$localVersion, version=${resp.version})"
            }
            return syncFull(
                apiService = apiService,
                databaseService = databaseService,
                userId = userId,
                versionID = resp.versionID,
                version = resp.version,
            )
        }

        var uidList =
            databaseService.getVersionSync(VERSION_TABLE, userId)?.uidList.orEmpty().toMutableList()
        resp.delete.orEmpty().forEach { conversationId ->
            databaseService.deleteConversation(conversationId)
            uidList.remove(conversationId)
        }
        val changed = mutableListOf<ConversationInfo>()
        (resp.insert.orEmpty() + resp.update.orEmpty()).forEach { conversation ->
            if (shouldSkipConversation(conversation, userId)) return@forEach
            val normalized = conversation.normalizedForStorage()
            databaseService.insertOrReplaceConversation(normalized)
            if (conversation.conversationID !in uidList) {
                uidList.add(conversation.conversationID)
            }
            if ((normalized.latestMsgSendTime ?: 0) > 0) {
                changed.add(normalized)
            }
        }
        changed.forEach { eventEmitter.emitConversation(ConversationEvent.Changed(it)) }

        if (resp.version > 0 || resp.versionID.isNotEmpty()) {
            databaseService.setVersionSync(
                tableName = VERSION_TABLE,
                entityId = userId,
                versionID = resp.versionID,
                version = resp.version,
                uidList = uidList,
            )
        }

        val visibleCount = databaseService.getVisibleConversations().size
        log.info { "incremental conversation sync finished: visible=$visibleCount" }
        return visibleCount
    }

    private suspend fun syncFull(
        apiService: ConversationSyncApi,
        databaseService: DatabaseService,
        userId: String,
        versionID: String = "",
        version: Int = 0,
    ): Int {
        val remote = apiService.getAllConversations(userId)
        var inserted = 0
        remote.forEach { conversation ->
            if (shouldSkipConversation(conversation, userId)) return@forEach
            databaseService.insertOrReplaceConversation(conversation.normalizedForStorage())
            inserted++
        }
        val uidList = runCatching { apiService.getFullConversationIDs(userId) }
            .getOrElse { error ->
                log.warn(error) { "getFullConversationIDs failed, falling back to local ids" }
                remote.map { it.conversationID }
            }
        if (version > 0 || versionID.isNotEmpty()) {
            databaseService.setVersionSync(
                tableName = VERSION_TABLE,
                entityId = userId,
                versionID = versionID,
                version = version,
                uidList = uidList,
            )
        } else {
            databaseService.deleteVersionSync(VERSION_TABLE, userId)
        }
        val visibleCount = databaseService.getVisibleConversations().size
        log.info { "full conversation sync finished: remote=$inserted visible=$visibleCount" }
        return visibleCount
    }

    private fun shouldSkipConversation(conversation: ConversationInfo, userId: String): Boolean {
        return conversation.conversationType == ConversationType.SINGLE &&
                conversation.userID == userId
    }

    private suspend fun emitTotalUnread(
        databaseService: DatabaseService,
        eventEmitter: SdkEventEmitter
    ) {
        val total = databaseService.getTotalUnreadCount()
        eventEmitter.emitConversation(ConversationEvent.TotalUnreadChanged(total))
    }
}
