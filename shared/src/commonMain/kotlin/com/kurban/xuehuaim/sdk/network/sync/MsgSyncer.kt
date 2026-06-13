package com.kurban.xuehuaim.sdk.network.sync

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.enum.ConnectionState
import com.kurban.xuehuaim.sdk.enum.ConversationType
import com.kurban.xuehuaim.sdk.enum.MessageType
import com.kurban.xuehuaim.sdk.event.ConversationEvent
import com.kurban.xuehuaim.sdk.event.MessageEvent
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.manager.CallManager
import com.kurban.xuehuaim.sdk.model.Message
import com.kurban.xuehuaim.sdk.network.http.ConversationSeqInfo
import com.kurban.xuehuaim.sdk.network.http.ImApiService
import com.kurban.xuehuaim.sdk.network.notify.NotificationDispatcher
import com.kurban.xuehuaim.sdk.network.ws.WebSocketService
import com.kurban.xuehuaim.sdk.platform.ioDispatcher
import com.kurban.xuehuaim.sdk.platform.sdkScope
import com.kurban.xuehuaim.sdk.network.ws.WsIdentifier
import com.kurban.xuehuaim.sdk.network.ws.WsRequest
import com.kurban.xuehuaim.sdk.sync.ConversationMinSeqSync
import com.kurban.xuehuaim.sdk.sync.ConversationSync
import com.kurban.xuehuaim.sdk.sync.FriendSync
import com.kurban.xuehuaim.sdk.sync.GroupSync
import com.kurban.xuehuaim.sdk.sync.MessageDisplayEnricher
import com.kurban.xuehuaim.sdk.sync.MessageSeqSync
import com.kurban.xuehuaim.sdk.sync.MessageSyncMutex
import com.kurban.xuehuaim.sdk.util.ConversationMessageUpdater
import com.kurban.xuehuaim.sdk.util.SdkLogger
import com.kurban.xuehuaim.sdk.util.withParsedContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

internal class MsgSyncer(
    private val webSocketService: WebSocketService,
    private val databaseService: DatabaseService,
    private val apiService: ImApiService,
    private val notificationDispatcher: NotificationDispatcher,
    private val eventEmitter: SdkEventEmitter,
) {
    private val log = SdkLogger.tag("MsgSyncer")
    private var userId: String = ""
    private var callManager: CallManager? = null
    private var backgroundSyncHandler: (suspend () -> Unit)? = null
    private val syncedMaxSeqs = mutableMapOf<String, Long>()
    private var reinstalled = false
    private val isSyncingLock = Mutex()
    private var isSyncing = false

    internal fun syncedMaxSeq(conversationId: String): Long? = syncedMaxSeqs[conversationId]
    internal fun isReinstalled(): Boolean = reinstalled

    fun bindUser(userId: String) {
        this.userId = userId
    }

    fun bindCallManager(manager: CallManager) {
        callManager = manager
    }

    fun bindBackgroundSync(handler: suspend () -> Unit) {
        backgroundSyncHandler = handler
    }

    suspend fun loadSeq() = withContext(ioDispatcher) {
        syncedMaxSeqs.clear()
        syncedMaxSeqs.putAll(databaseService.getAllConversationMaxNormalMsgSeqs())
        syncedMaxSeqs.putAll(databaseService.getAllNotificationSeqs())
        val conversations = databaseService.getAllConversations()
        val installed = databaseService.selectKv(MessageSeqSync.SDK_INSTALLED_KV_KEY, isGlobal = true) == "1"
        reinstalled = conversations.isEmpty() && !installed
        log.info { "loadSeq: synced=${syncedMaxSeqs.size} reinstalled=$reinstalled" }
    }

    suspend fun start() {
        webSocketService.onPushMsg = { response -> handlePush(response.data) }
        webSocketService.onConnected = { doConnectedSync() }
    }

    suspend fun doConnectedSync() = withContext(ioDispatcher) {
        if (userId.isEmpty()) return@withContext
        if (!startSync()) {
            log.info { "connected sync skipped: already syncing" }
            return@withContext
        }
        eventEmitter.emitConversation(ConversationEvent.SyncStarted(reinstalled = reinstalled))
        var syncFailed = false
        if (!reinstalled) {
            eventEmitter.emitConversation(ConversationEvent.SyncProgress(10))
        }
        runCatching {
            ConversationSync.syncFromServer(
                apiService = apiService,
                databaseService = databaseService,
                eventEmitter = eventEmitter,
                userId = userId,
            )
        }.onFailure { e ->
            syncFailed = true
            log.error(e) { "conversation sync failed" }
        }
        if (!reinstalled) {
            eventEmitter.emitConversation(ConversationEvent.SyncProgress(50))
        }
        runCatching {
            FriendSync.syncFriends(apiService, databaseService, eventEmitter, userId)
            FriendSync.syncBlackList(apiService, databaseService, userId)
        }.onFailure { e ->
            syncFailed = true
            log.error(e) { "friend sync failed" }
        }
        runCatching {
            GroupSync.syncJoinedGroups(apiService, databaseService, eventEmitter, userId)
        }.onFailure { e ->
            syncFailed = true
            log.error(e) { "group sync failed" }
        }
        if (!reinstalled) {
            eventEmitter.emitConversation(ConversationEvent.SyncProgress(70))
        } else {
            eventEmitter.emitConversation(ConversationEvent.SyncProgress(REINSTALL_INIT_PROGRESS))
        }
        runCatching {
            syncMessageGapsFromServer(MessageSeqSync.CONNECT_PULL_NUMS)
        }.onFailure { e ->
            syncFailed = true
            log.error(e) { "message gap sync failed" }
        }
        val wasReinstalled = reinstalled
        if (syncFailed) {
            eventEmitter.emitConversation(ConversationEvent.SyncFailed("partial sync failed"))
        }
        if (!wasReinstalled) {
            eventEmitter.emitConversation(ConversationEvent.SyncProgress(90))
        }
        runCatching { syncLatestMessagesForHiddenConversations() }
            .onFailure { e -> log.warn(e) { "latest message pull failed" } }
        if (wasReinstalled) {
            databaseService.insertOrReplaceKv(MessageSeqSync.SDK_INSTALLED_KV_KEY, "1", isGlobal = true)
            reinstalled = false
            eventEmitter.emitConversation(ConversationEvent.SyncProgress(100))
        }
        val visibleCount = databaseService.getVisibleConversations().size
        log.info { "connected sync finished: visible=$visibleCount reinstalled=$wasReinstalled" }
        eventEmitter.emitConversation(ConversationEvent.SyncFinished(visibleCount, reinstalled = wasReinstalled))
        backgroundSyncHandler?.let { handler ->
            sdkScope.launch {
                runCatching { handler() }
                    .onFailure { e -> log.warn(e) { "background sync failed" } }
            }
        }
    }

    suspend fun triggerWakeupSync() = withContext(ioDispatcher) {
        if (userId.isEmpty()) return@withContext
        if (!startSync()) return@withContext
        log.info { "triggerWakeupSync" }
        runCatching {
            ConversationSync.applyServerSeqs(
                apiService = apiService,
                databaseService = databaseService,
                eventEmitter = eventEmitter,
                userId = userId,
            )
            syncMessageGapsFromServer(MessageSeqSync.DEFAULT_PULL_NUMS)
        }.onFailure { e -> log.warn(e) { "wakeup sync failed" } }
    }

    suspend fun handlePush(data: ByteArray) = withContext(ioDispatcher) {
        if (data.isEmpty()) return@withContext
        try {
            val pullResp = decodePushMessages(data)
                ?: run {
                    log.warn { "handle push failed: unable to decode push payload" }
                    return@withContext
                }
            handlePushPullResp(pullResp)
        } catch (e: Exception) {
            log.error(e) { "handle push failed" }
        }
    }

    suspend fun pullMessagesBySeqList(
        conversationId: String,
        begin: Long,
        end: Long,
        num: Long? = null,
        order: Int = 1,
    ) = withContext(ioDispatcher) {
        pullMessagesBySeqRanges(
            listOf(
                SeqRange(
                    conversationID = conversationId,
                    begin = begin,
                    end = end,
                    num = num,
                ),
            ),
            order = order,
        )
    }

    suspend fun pullMessagesByLostSeqs(
        conversationId: String,
        lostSeqs: List<Long>,
        order: Int = 1,
        isReverse: Boolean = false,
    ) = withContext(ioDispatcher) {
        if (lostSeqs.isEmpty() || userId.isEmpty()) return@withContext
        lostSeqs.chunked(MessageSeqSync.SPLIT_PULL_MSG_NUM).forEach { chunk ->
            val pullResp = pullLostSeqChunk(conversationId, chunk, order)
            val decoded = decodePullMsgsContent(pullResp)
            processPullMsgResp(decoded, bootstrapOnly = true)
            decoded.msgs[conversationId]?.let { msgList ->
                ConversationMinSeqSync.applyMinSeqFromPull(
                    databaseService = databaseService,
                    conversationId = conversationId,
                    msgList = msgList,
                    isReverse = isReverse,
                )
            }
        }
    }

    suspend fun syncMessageGapsFromServer(pullNums: Long) = MessageSyncMutex.withLock {
        val seqResp = apiService.getConversationsHasReadAndMaxSeq(userId, emptyList())
        syncMessageGaps(seqResp.seqs, pullNums)
    }

    suspend fun syncMessageGaps(
        serverSeqs: Map<String, ConversationSeqInfo>,
        pullNums: Long,
    ) = MessageSyncMutex.withLock {
        val isReinstallSync = reinstalled
        if (isReinstallSync) {
            val notificationSeqs = MessageSeqSync.notificationSeqsFromServer(
                MessageSeqSync.serverMaxSeqMap(serverSeqs),
            )
            notificationSeqs.forEach { (conversationId, seq) ->
                databaseService.setNotificationSeq(conversationId, seq)
                syncedMaxSeqs[conversationId] = seq
            }
        }
        val needSync = MessageSeqSync.buildNeedSyncSeqMap(
            syncedMaxSeqs = syncedMaxSeqs,
            serverMaxSeqs = MessageSeqSync.serverMaxSeqMap(serverSeqs),
            reinstalled = isReinstallSync,
        )
        if (isReinstallSync) {
            pullAndProcessReinstallGaps(needSync, pullNums)
        } else {
            pullAndProcessGaps(needSync, pullNums)
        }
    }

    private suspend fun handlePushPullResp(pullResp: com.kurban.xuehuaim.sdk.network.sync.PullMsgResp) {
        val needSync = linkedMapOf<String, LongRange>()
        val contiguousMsgs = mutableMapOf<String, MsgList>()

        pullResp.msgs.forEach { (conversationId, msgList) ->
            collectPushMessages(conversationId, msgList.msgs, needSync, contiguousMsgs)
        }
        if (contiguousMsgs.isNotEmpty()) {
            processPullMsgResp(
                com.kurban.xuehuaim.sdk.network.sync.PullMsgResp(msgs = contiguousMsgs),
                bootstrapOnly = false,
            )
        }
        if (needSync.isNotEmpty()) {
            pullAndProcessGaps(needSync, MessageSeqSync.DEFAULT_PULL_NUMS)
        }

        val notificationNeedSync = linkedMapOf<String, LongRange>()
        val notificationContiguous = mutableMapOf<String, MsgList>()
        pullResp.notificationMsgs.forEach { (conversationId, msgList) ->
            collectPushMessages(conversationId, msgList.msgs, notificationNeedSync, notificationContiguous)
        }
        notificationContiguous.forEach { (id, list) ->
            list.msgs.forEach { wsMsg ->
                val message = wsMsg.toMessage(id)
                if (message.isNotification()) notificationDispatcher.dispatch(message)
            }
        }
        if (notificationNeedSync.isNotEmpty()) {
            pullAndProcessGaps(notificationNeedSync, MessageSeqSync.DEFAULT_PULL_NUMS)
        }
    }

    private suspend fun collectPushMessages(
        conversationId: String,
        msgs: List<WsMsgData>,
        needSync: MutableMap<String, LongRange>,
        contiguous: MutableMap<String, MsgList>,
    ) {
        val storageMsgs = mutableListOf<WsMsgData>()
        var lastSeq = 0L
        for (msg in msgs) {
            if (msg.seq == 0L) {
                processPullMsgResp(
                    com.kurban.xuehuaim.sdk.network.sync.PullMsgResp(
                        msgs = mapOf(conversationId to MsgList(listOf(msg))),
                    ),
                    bootstrapOnly = false,
                )
                continue
            }
            lastSeq = msg.seq
            storageMsgs.add(msg)
        }
        if (storageMsgs.isEmpty()) return
        val synced = syncedMaxSeqs[conversationId] ?: 0L
        val expectedLast = synced + storageMsgs.size
        if (lastSeq == expectedLast) {
            val merged = contiguous[conversationId]?.msgs.orEmpty() + storageMsgs
            contiguous[conversationId] = MsgList(merged)
            syncedMaxSeqs[conversationId] = lastSeq
        } else if (lastSeq > synced) {
            needSync[conversationId] = (synced + 1)..lastSeq
        }
    }

    private suspend fun pullAndProcessReinstallGaps(needSync: Map<String, LongRange>, pullNums: Long) {
        if (needSync.isEmpty() || userId.isEmpty()) return
        val total = needSync.size
        var processed = 0
        var batch = linkedMapOf<String, LongRange>()
        var msgNum = 0
        for ((conversationId, range) in needSync) {
            batch[conversationId] = range
            val oneConversationSyncNum = range.last - range.first + 1
            msgNum += if (MessageSeqSync.isNotificationConversation(conversationId)) {
                oneConversationSyncNum.toInt()
            } else {
                minOf(oneConversationSyncNum, pullNums).toInt()
            }
            if (msgNum >= MessageSeqSync.SPLIT_PULL_MSG_NUM) {
                executeGapPull(batch, pullNums)
                processed += batch.size
                emitReinstallProgress(processed, total)
                batch = linkedMapOf()
                msgNum = 0
            }
        }
        if (batch.isNotEmpty()) {
            executeGapPull(batch, pullNums)
            processed += batch.size
            emitReinstallProgress(processed, total)
        }
    }

    private suspend fun emitReinstallProgress(processed: Int, total: Int) {
        if (total <= 0) return
        val progress = (processed * (100 - REINSTALL_INIT_PROGRESS)) / total + REINSTALL_INIT_PROGRESS
        eventEmitter.emitConversation(ConversationEvent.SyncProgress(progress.coerceIn(0, 100)))
    }

    private suspend fun pullAndProcessGaps(needSync: Map<String, LongRange>, pullNums: Long) {
        if (needSync.isEmpty() || userId.isEmpty()) return
        var batch = linkedMapOf<String, LongRange>()
        var msgNum = 0
        for ((conversationId, range) in needSync) {
            batch[conversationId] = range
            val oneConversationSyncNum = range.last - range.first + 1
            msgNum += if (MessageSeqSync.isNotificationConversation(conversationId)) {
                oneConversationSyncNum.toInt()
            } else {
                minOf(oneConversationSyncNum, pullNums).toInt()
            }
            if (msgNum >= MessageSeqSync.SPLIT_PULL_MSG_NUM) {
                executeGapPull(batch, pullNums)
                batch = linkedMapOf()
                msgNum = 0
            }
        }
        if (batch.isNotEmpty()) {
            executeGapPull(batch, pullNums)
        }
    }

    private suspend fun executeGapPull(ranges: Map<String, LongRange>, pullNums: Long) {
        val seqRanges = ranges.map { (conversationId, range) ->
            SeqRange(
                conversationID = conversationId,
                begin = range.first,
                end = range.last,
                num = if (MessageSeqSync.isNotificationConversation(conversationId)) {
                    range.last - range.first + 1
                } else {
                    pullNums
                },
            )
        }
        pullMessagesBySeqRanges(seqRanges, order = 0)
        ranges.forEach { (conversationId, range) ->
            syncedMaxSeqs[conversationId] = maxOf(syncedMaxSeqs[conversationId] ?: 0L, range.last)
        }
    }

    private suspend fun syncLatestMessagesForHiddenConversations() {
        val hidden = databaseService.getAllConversations().filter { conversation ->
            !conversation.conversationID.startsWith("n_") &&
                    conversation.maxSeq > 0 &&
                    (conversation.latestMsgSendTime ?: 0) == 0L
        }
        if (hidden.isEmpty()) return
        hidden.chunked(SEQ_RANGE_BATCH_SIZE).forEach { batch ->
            val seqRanges = batch.map { conversation ->
                SeqRange(
                    conversationID = conversation.conversationID,
                    begin = 0,
                    end = conversation.maxSeq,
                    num = 1,
                )
            }
            runCatching { pullMessagesBySeqRanges(seqRanges, order = 0) }
                .onFailure { e -> log.warn(e) { "pull batch failed (${seqRanges.size} ranges)" } }
        }
    }

    private suspend fun pullMessagesBySeqRanges(seqRanges: List<SeqRange>, order: Int = 1) {
        if (seqRanges.isEmpty() || userId.isEmpty()) return
        val pullResp = if (isWebSocketConnected()) {
            runCatching { pullMsgByRangeWs(seqRanges, order) }
                .getOrElse { error ->
                    log.warn(error) { "WS pull by range failed, fallback to HTTP" }
                    apiService.pullMsgBySeqs(userID = userId, seqRanges = seqRanges, order = order)
                }
        } else {
            apiService.pullMsgBySeqs(userID = userId, seqRanges = seqRanges, order = order)
        }
        val decoded = decodePullMsgsContent(pullResp)
        processPullMsgResp(decoded, bootstrapOnly = true)
    }

    private suspend fun pullLostSeqChunk(
        conversationId: String,
        seqs: List<Long>,
        order: Int,
    ): PullMsgResp {
        if (isWebSocketConnected()) {
            return runCatching { pullMsgBySeqListWs(conversationId, seqs, order) }
                .getOrElse { error ->
                    log.warn(error) { "WS pull by seq list failed, fallback to HTTP" }
                    pullLostSeqChunkHttp(conversationId, seqs, order)
                }
        }
        return pullLostSeqChunkHttp(conversationId, seqs, order)
    }

    private suspend fun pullLostSeqChunkHttp(
        conversationId: String,
        seqs: List<Long>,
        order: Int,
    ): PullMsgResp {
        val ranges = seqs.map { seq ->
            SeqRange(conversationID = conversationId, begin = seq, end = seq, num = 1)
        }
        return apiService.pullMsgBySeqs(userID = userId, seqRanges = ranges, order = order)
    }

    private suspend fun pullMsgByRangeWs(seqRanges: List<SeqRange>, order: Int): PullMsgResp {
        val response = webSocketService.sendRequest(
            WsRequest(
                reqIdentifier = WsIdentifier.PULL_MSG_BY_RANGE,
                data = encodePullMessageBySeqsReq(userId, seqRanges, order),
            ),
        )
        if (!response.isSuccess) {
            throw IllegalStateException("WS pull by range failed: ${response.errCode} ${response.errMsg}")
        }
        return decodePullMessageBySeqsResp(response.data)
            ?: throw IllegalStateException("WS pull by range decode failed")
    }

    private suspend fun pullMsgBySeqListWs(
        conversationId: String,
        seqs: List<Long>,
        order: Int,
    ): PullMsgResp {
        val response = webSocketService.sendRequest(
            WsRequest(
                reqIdentifier = WsIdentifier.PULL_MSG_BY_SEQ_LIST,
                data = encodeGetSeqMessageReq(userId, conversationId, seqs, order),
            ),
        )
        if (!response.isSuccess) {
            throw IllegalStateException("WS pull by seq list failed: ${response.errCode} ${response.errMsg}")
        }
        return decodeGetSeqMessageResp(response.data)
            ?: throw IllegalStateException("WS pull by seq list decode failed")
    }

    private fun isWebSocketConnected(): Boolean =
        eventEmitter.connectionState.value == ConnectionState.CONNECTED

    private suspend fun processPullMsgResp(pullResp: PullMsgResp, bootstrapOnly: Boolean = false) {
        pullResp.msgs.forEach { (conversationId, msgList) ->
            msgList.msgs.forEach { wsMsg ->
                val message = wsMsg.toMessage(conversationId)
                if (message.contentType == MessageType.TYPING) {
                    notificationDispatcher.dispatchTyping(message, conversationId)
                    return@forEach
                }
                if (isOnlineOnlyMessage(wsMsg, message)) {
                    if (message.isNotification() && !bootstrapOnly) {
                        notificationDispatcher.dispatch(message)
                    }
                    if (!bootstrapOnly) {
                        val enriched = MessageDisplayEnricher.enrichMessages(
                            apiService = apiService,
                            databaseService = databaseService,
                            messages = listOf(message),
                        ).first()
                        eventEmitter.emitMessage(MessageEvent.OnlineOnlyReceived(enriched))
                        if (enriched.contentType == MessageType.CUSTOM ||
                            enriched.contentType == MessageType.CUSTOM_MSG_ONLINE_ONLY
                        ) {
                            val senderId = enriched.sendID.orEmpty()
                            val payload = enriched.content ?: enriched.customElem?.data.orEmpty()
                            callManager?.handleSignalingMessage(senderId, payload)
                        }
                    }
                    return@forEach
                }
                if (message.isNotification()) {
                    if (!bootstrapOnly) {
                        notificationDispatcher.dispatch(message)
                    }
                    if (message.seq > 0) {
                        syncedMaxSeqs[conversationId] =
                            maxOf(syncedMaxSeqs[conversationId] ?: 0L, message.seq)
                    }
                    return@forEach
                }
                val enriched = MessageDisplayEnricher.enrichMessages(
                    apiService = apiService,
                    databaseService = databaseService,
                    messages = listOf(message),
                ).first()
                databaseService.insertOrReplaceMessage(enriched)
                if (enriched.seq > 0) {
                    syncedMaxSeqs[conversationId] =
                        maxOf(syncedMaxSeqs[conversationId] ?: 0L, enriched.seq)
                }
                ConversationMessageUpdater.updateFromMessage(
                    databaseService = databaseService,
                    eventEmitter = eventEmitter,
                    message = enriched,
                    selfUserId = userId,
                )
                if (!bootstrapOnly) {
                    eventEmitter.emitMessage(MessageEvent.Received(enriched))
                    if (enriched.contentType == MessageType.CUSTOM) {
                        val senderId = enriched.sendID.orEmpty()
                        val payload = enriched.content ?: enriched.customElem?.data.orEmpty()
                        callManager?.handleSignalingMessage(senderId, payload)
                    }
                }
            }
        }
        if (!bootstrapOnly) {
            pullResp.notificationMsgs.forEach { (conversationId, msgList) ->
                msgList.msgs.forEach { wsMsg ->
                    val message = wsMsg.toMessage(conversationId)
                    if (message.isNotification()) {
                        notificationDispatcher.dispatch(message)
                    }
                    if (message.seq > 0) {
                        syncedMaxSeqs[conversationId] =
                            maxOf(syncedMaxSeqs[conversationId] ?: 0L, message.seq)
                    }
                }
            }
        }
    }

    private suspend fun startSync(): Boolean = isSyncingLock.withLock {
        if (isSyncing) return@withLock false
        isSyncing = true
        sdkScope.launch {
            delay(5.seconds)
            isSyncingLock.withLock { isSyncing = false }
        }
        true
    }

    private fun Message.isNotification(): Boolean {
        val type = contentType?.value ?: return false
        return type >= MessageType.NOTIFICATION_BEGIN.value
    }

    private fun isOnlineOnlyMessage(wsMsg: WsMsgData, message: Message): Boolean {
        if (message.contentType == MessageType.CUSTOM_MSG_ONLINE_ONLY) return true
        val isHistory = wsMsg.options["history"] ?: true
        return !isHistory
    }

    private fun WsMsgData.toMessage(conversationId: String): Message = Message(
        clientMsgID = clientMsgID,
        serverMsgID = serverMsgID,
        sendID = sendID,
        recvID = recvID,
        groupID = groupID,
        senderNickname = senderNickname,
        senderFaceUrl = senderFaceURL,
        contentType = contentType?.let { v ->
            MessageType.entries.find { it.value == v }
        },
        content = content,
        seq = seq,
        sendTime = sendTime,
        createTime = createTime,
        sessionType = sessionType?.let { v ->
            ConversationType.entries.find { it.value == v }
        },
        conversationID = conversationId,
    ).withParsedContent()

    private companion object {
        const val SEQ_RANGE_BATCH_SIZE = 50
        const val REINSTALL_INIT_PROGRESS = 10
    }
}
