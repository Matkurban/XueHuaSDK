package com.kurban.xuehuaim.sdk.network.sync

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.enum.ConversationType
import com.kurban.xuehuaim.sdk.enum.MessageType
import com.kurban.xuehuaim.sdk.event.ConversationEvent
import com.kurban.xuehuaim.sdk.event.MessageEvent
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.manager.CallManager
import com.kurban.xuehuaim.sdk.model.Message
import com.kurban.xuehuaim.sdk.network.http.ImApiService
import com.kurban.xuehuaim.sdk.network.notify.NotificationDispatcher
import com.kurban.xuehuaim.sdk.network.ws.WebSocketService
import com.kurban.xuehuaim.sdk.platform.ioDispatcher
import com.kurban.xuehuaim.sdk.sync.ConversationSync
import com.kurban.xuehuaim.sdk.sync.FriendSync
import com.kurban.xuehuaim.sdk.sync.GroupSync
import com.kurban.xuehuaim.sdk.sync.MessageDisplayEnricher
import com.kurban.xuehuaim.sdk.util.ConversationMessageUpdater
import com.kurban.xuehuaim.sdk.util.SdkLogger
import com.kurban.xuehuaim.sdk.util.withParsedContent
import kotlinx.coroutines.withContext

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
    private val conversationMaxSeq = mutableMapOf<String, Long>()

    fun bindUser(userId: String) {
        this.userId = userId
    }

    fun bindCallManager(manager: CallManager) {
        callManager = manager
    }

    suspend fun start() {
        webSocketService.onPushMsg = { response -> handlePush(response.data) }
        webSocketService.onConnected = { doConnectedSync() }
    }

    suspend fun doConnectedSync() = withContext(ioDispatcher) {
        if (userId.isEmpty()) return@withContext
        eventEmitter.emitConversation(ConversationEvent.SyncStarted)
        try {
            eventEmitter.emitConversation(ConversationEvent.SyncProgress(10))
            ConversationSync.syncFromServer(
                apiService = apiService,
                databaseService = databaseService,
                eventEmitter = eventEmitter,
                userId = userId,
            )
            eventEmitter.emitConversation(ConversationEvent.SyncProgress(60))
            FriendSync.syncFriends(apiService, databaseService, eventEmitter, userId)
            FriendSync.syncBlackList(apiService, databaseService, userId)
            GroupSync.syncJoinedGroups(apiService, databaseService, eventEmitter, userId)
            eventEmitter.emitConversation(ConversationEvent.SyncProgress(80))
        } catch (e: Exception) {
            log.error(e) { "connected sync failed" }
            eventEmitter.emitConversation(ConversationEvent.SyncFailed(e.message ?: "sync failed"))
            return@withContext
        }
        runCatching { syncLatestMessagesForHiddenConversations() }
            .onFailure { e -> log.warn(e) { "latest message pull failed" } }
        val visibleCount = databaseService.getVisibleConversations().size
        log.info { "connected sync finished: visible=$visibleCount" }
        eventEmitter.emitConversation(ConversationEvent.SyncFinished(visibleCount))
    }

    suspend fun triggerWakeupSync() = withContext(ioDispatcher) {
        if (userId.isEmpty()) return@withContext
        log.info { "triggerWakeupSync" }
        runCatching { doConnectedSync() }
            .onFailure { e -> log.warn(e) { "wakeup sync failed" } }
    }

    suspend fun handlePush(data: ByteArray) = withContext(ioDispatcher) {
        if (data.isEmpty()) return@withContext
        try {
            val pullResp = decodePushMessages(data)
                ?: run {
                    log.warn { "handle push failed: unable to decode push payload" }
                    return@withContext
                }
            processPullMsgResp(pullResp)
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

    private suspend fun syncLatestMessagesForHiddenConversations() {
        val hidden = databaseService.getAllConversations().filter { conversation ->
            !conversation.conversationID.startsWith("n_") &&
                    conversation.maxSeq > 0 &&
                    (conversation.latestMsgSendTime ?: 0) == 0L
        }
        if (hidden.isEmpty()) {
            log.info { "no hidden conversations need latest message pull" }
            return
        }
        log.info { "sync latest messages for ${hidden.size} hidden conversations" }
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
        val pullResp =
            apiService.pullMsgBySeqs(userID = userId, seqRanges = seqRanges, order = order)
        val decoded = decodePullMsgsContent(pullResp)
        processPullMsgResp(decoded, bootstrapOnly = true)
    }

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
                    return@forEach
                }
                val enriched = MessageDisplayEnricher.enrichMessages(
                    apiService = apiService,
                    databaseService = databaseService,
                    messages = listOf(message),
                ).first()
                databaseService.insertOrReplaceMessage(enriched)
                conversationMaxSeq[conversationId] =
                    maxOf(conversationMaxSeq[conversationId] ?: 0L, enriched.seq)
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
                }
            }
        }
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
    }
}
