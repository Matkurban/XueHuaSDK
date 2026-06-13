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

        applyServerSeqs(apiService, databaseService, userId)
        ConversationDisplayEnricher.enrichConversationDisplayNames(
            apiService = apiService,
            databaseService = databaseService,
            eventEmitter = eventEmitter,
            userId = userId,
        )
        emitTotalUnread(databaseService, eventEmitter)
        return synced
    }

    private suspend fun syncIncremental(
        apiService: ImApiService,
        databaseService: DatabaseService,
        eventEmitter: SdkEventEmitter,
        userId: String,
        localVersion: Int,
        localVersionId: String,
    ): Int {
        val resp = apiService.getIncrementalConversations(userId, localVersion, localVersionId)
        if (resp.full || localVersion == 0) {
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

        resp.delete.orEmpty().forEach { databaseService.deleteConversation(it) }
        val changed = mutableListOf<ConversationInfo>()
        (resp.insert.orEmpty() + resp.update.orEmpty()).forEach { conversation ->
            if (shouldSkipConversation(conversation, userId)) return@forEach
            val normalized = conversation.normalizedForStorage()
            databaseService.insertOrReplaceConversation(normalized)
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
            )
        }

        val visibleCount = databaseService.getVisibleConversations().size
        log.info { "incremental conversation sync finished: visible=$visibleCount" }
        return visibleCount
    }

    private suspend fun syncFull(
        apiService: ImApiService,
        databaseService: DatabaseService,
        userId: String,
        versionID: String = "",
        version: Int = 0,
    ): Int {
        val remote = apiService.getAllConversations(userId)
        val localUnread =
            databaseService.getAllConversations().associate { it.conversationID to it.unreadCount }
        var inserted = 0
        remote.forEach { conversation ->
            if (shouldSkipConversation(conversation, userId)) return@forEach
            val normalized = conversation.normalizedForStorage().let { conv ->
                val preservedUnread = localUnread[conv.conversationID]
                if (preservedUnread != null) conv.copy(unreadCount = preservedUnread) else conv
            }
            databaseService.insertOrReplaceConversation(normalized)
            inserted++
        }
        if (version > 0 || versionID.isNotEmpty()) {
            databaseService.setVersionSync(
                tableName = VERSION_TABLE,
                entityId = userId,
                versionID = versionID,
                version = version,
            )
        } else {
            databaseService.deleteVersionSync(VERSION_TABLE, userId)
        }
        val visibleCount = databaseService.getVisibleConversations().size
        log.info { "full conversation sync finished: remote=$inserted visible=$visibleCount" }
        return visibleCount
    }

    private suspend fun applyServerSeqs(
        apiService: ImApiService,
        databaseService: DatabaseService,
        userId: String,
    ) {
        val seqResp = runCatching {
            apiService.getConversationsHasReadAndMaxSeq(userId, emptyList())
        }.getOrElse { error ->
            log.error(error) { "getConversationsHasReadAndMaxSeq failed" }
            return
        }
        seqResp.seqs.forEach { (conversationId, seqInfo) ->
            val existing = databaseService.getConversation(conversationId) ?: return@forEach
            val effectiveHasReadSeq = maxOf(existing.hasReadSeq, seqInfo.hasReadSeq)
            databaseService.insertOrReplaceConversation(
                existing.copy(
                    maxSeq = seqInfo.maxSeq,
                    hasReadSeq = effectiveHasReadSeq,
                ),
            )
        }
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
