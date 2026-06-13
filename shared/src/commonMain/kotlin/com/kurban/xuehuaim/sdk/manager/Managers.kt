package com.kurban.xuehuaim.sdk.manager

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.enum.ConnectionState
import com.kurban.xuehuaim.sdk.enum.ConversationType
import com.kurban.xuehuaim.sdk.enum.GroupAtType
import com.kurban.xuehuaim.sdk.enum.MessageStatus
import com.kurban.xuehuaim.sdk.enum.MessageType
import com.kurban.xuehuaim.sdk.enum.ReceiveMessageOpt
import com.kurban.xuehuaim.sdk.enum.SdkErrorCode
import com.kurban.xuehuaim.sdk.exception.XueHuaException
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.model.AppealInfo
import com.kurban.xuehuaim.sdk.model.ApplicationVersionInfo
import com.kurban.xuehuaim.sdk.model.AtTextElem
import com.kurban.xuehuaim.sdk.model.AtUserInfo
import com.kurban.xuehuaim.sdk.model.AuthCacheData
import com.kurban.xuehuaim.sdk.model.BlacklistInfo
import com.kurban.xuehuaim.sdk.model.ConversationInfo
import com.kurban.xuehuaim.sdk.model.ConversationReq
import com.kurban.xuehuaim.sdk.model.CustomElem
import com.kurban.xuehuaim.sdk.model.FavoriteItem
import com.kurban.xuehuaim.sdk.model.FileElem
import com.kurban.xuehuaim.sdk.model.FriendApplicationInfo
import com.kurban.xuehuaim.sdk.model.FriendInfo
import com.kurban.xuehuaim.sdk.model.GroupApplicationInfo
import com.kurban.xuehuaim.sdk.model.GroupInfo
import com.kurban.xuehuaim.sdk.model.GroupMemberInfo
import com.kurban.xuehuaim.sdk.model.MergeElem
import com.kurban.xuehuaim.sdk.model.Message
import com.kurban.xuehuaim.sdk.model.MomentInfo
import com.kurban.xuehuaim.sdk.model.PictureElem
import com.kurban.xuehuaim.sdk.model.PictureInfo
import com.kurban.xuehuaim.sdk.model.PointsTransaction
import com.kurban.xuehuaim.sdk.model.QuoteElem
import com.kurban.xuehuaim.sdk.model.RedPacketInfo
import com.kurban.xuehuaim.sdk.model.ReportInfo
import com.kurban.xuehuaim.sdk.model.SearchResult
import com.kurban.xuehuaim.sdk.model.SoundElem
import com.kurban.xuehuaim.sdk.model.TextElem
import com.kurban.xuehuaim.sdk.model.UserFullInfo
import com.kurban.xuehuaim.sdk.model.UserInfo
import com.kurban.xuehuaim.sdk.model.VideoElem
import com.kurban.xuehuaim.sdk.network.http.ImApiService
import com.kurban.xuehuaim.sdk.network.http.LoginUserIdProvider
import com.kurban.xuehuaim.sdk.network.notify.NotificationDispatcher
import com.kurban.xuehuaim.sdk.network.sync.MsgSyncer
import com.kurban.xuehuaim.sdk.network.sync.SendMsgReqData
import com.kurban.xuehuaim.sdk.network.sync.decodeUserSendMsgResp
import com.kurban.xuehuaim.sdk.network.sync.encodeSendMsgReq
import com.kurban.xuehuaim.sdk.network.ws.WebSocketService
import com.kurban.xuehuaim.sdk.network.ws.WsIdentifier
import com.kurban.xuehuaim.sdk.network.ws.WsRequest
import com.kurban.xuehuaim.sdk.platform.currentPlatform
import com.kurban.xuehuaim.sdk.platform.ioDispatcher
import com.kurban.xuehuaim.sdk.sync.MessageDisplayEnricher
import com.kurban.xuehuaim.sdk.util.ClientMsgIdGenerator
import com.kurban.xuehuaim.sdk.util.ConversationMessageUpdater
import com.kurban.xuehuaim.sdk.util.md5Hex
import com.kurban.xuehuaim.sdk.util.withParsedContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private fun LoginUserIdProvider.requireUserId(): String =
    this() ?: throw XueHuaException.from(SdkErrorCode.NOT_LOGIN)

class MessageManager internal constructor(
    private val apiService: ImApiService,
    private val databaseService: DatabaseService,
    private val webSocketService: WebSocketService,
    private val msgSyncer: MsgSyncer,
    private val notificationDispatcher: NotificationDispatcher,
    private val eventEmitter: SdkEventEmitter,
    private val loginUserId: LoginUserIdProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    suspend fun createTextMessage(text: String): Message = Message(
        clientMsgID = ClientMsgIdGenerator.generate(),
        contentType = MessageType.TEXT,
        content = Json.encodeToString(TextElem(text)),
        textElem = TextElem(text),
        createTime = com.kurban.xuehuaim.sdk.util.System.currentTimeMillis(),
    )

    suspend fun createImageMessage(imageUrl: String, width: Int = 0, height: Int = 0): Message {
        val elem = PictureElem(
            sourcePicture = PictureInfo(url = imageUrl, width = width, height = height),
            bigPicture = PictureInfo(url = imageUrl, width = width, height = height),
        )
        return Message(
            clientMsgID = ClientMsgIdGenerator.generate(),
            contentType = MessageType.PICTURE,
            content = Json.encodeToString(elem),
            pictureElem = elem,
            createTime = com.kurban.xuehuaim.sdk.util.System.currentTimeMillis(),
        )
    }

    suspend fun createSoundMessage(sourceUrl: String, duration: Long): Message {
        val elem = SoundElem(sourceUrl = sourceUrl, duration = duration)
        return Message(
            clientMsgID = ClientMsgIdGenerator.generate(),
            contentType = MessageType.VOICE,
            content = Json.encodeToString(elem),
            soundElem = elem,
            createTime = com.kurban.xuehuaim.sdk.util.System.currentTimeMillis(),
        )
    }

    suspend fun createVideoMessage(
        videoUrl: String,
        duration: Long,
        snapshotUrl: String? = null
    ): Message {
        val elem = VideoElem(videoUrl = videoUrl, duration = duration, snapshotUrl = snapshotUrl)
        return Message(
            clientMsgID = ClientMsgIdGenerator.generate(),
            contentType = MessageType.VIDEO,
            content = Json.encodeToString(elem),
            videoElem = elem,
            createTime = com.kurban.xuehuaim.sdk.util.System.currentTimeMillis(),
        )
    }

    suspend fun createFileMessage(
        sourceUrl: String,
        fileName: String,
        fileSize: Long = 0
    ): Message {
        val elem = FileElem(sourceUrl = sourceUrl, fileName = fileName, fileSize = fileSize)
        return Message(
            clientMsgID = ClientMsgIdGenerator.generate(),
            contentType = MessageType.FILE,
            content = Json.encodeToString(elem),
            fileElem = elem,
            createTime = com.kurban.xuehuaim.sdk.util.System.currentTimeMillis(),
        )
    }

    suspend fun createLocationMessage(
        description: String,
        latitude: Double,
        longitude: Double
    ): Message {
        val payload = """{"desc":"$description","latitude":$latitude,"longitude":$longitude}"""
        return Message(
            clientMsgID = ClientMsgIdGenerator.generate(),
            contentType = MessageType.LOCATION,
            content = payload,
            createTime = com.kurban.xuehuaim.sdk.util.System.currentTimeMillis(),
        )
    }

    suspend fun createCardMessage(
        userId: String,
        nickname: String,
        faceUrl: String? = null
    ): Message {
        val payload =
            """{"userID":"$userId","nickname":"$nickname","faceURL":"${faceUrl.orEmpty()}"}"""
        return Message(
            clientMsgID = ClientMsgIdGenerator.generate(),
            contentType = MessageType.CARD,
            content = payload,
            senderNickname = nickname,
            senderFaceUrl = faceUrl,
            createTime = com.kurban.xuehuaim.sdk.util.System.currentTimeMillis(),
        )
    }

    suspend fun createQuoteMessage(quoteMessage: Message, text: String): Message {
        val elem = QuoteElem(text = text, quoteMessage = quoteMessage)
        return Message(
            clientMsgID = ClientMsgIdGenerator.generate(),
            contentType = MessageType.QUOTE,
            content = Json.encodeToString(elem),
            quoteElem = elem,
            textElem = TextElem(text),
            createTime = com.kurban.xuehuaim.sdk.util.System.currentTimeMillis(),
        )
    }

    suspend fun createTextAtMessage(
        text: String,
        atUserIdList: List<String>,
        atUsersInfo: List<AtUserInfo>,
        quoteMessage: Message? = null,
    ): Message {
        val elem = AtTextElem(
            text = text,
            atUserList = atUserIdList,
            atUsersInfo = atUsersInfo,
            quoteMessage = quoteMessage,
        )
        return Message(
            clientMsgID = ClientMsgIdGenerator.generate(),
            contentType = MessageType.AT_TEXT,
            content = Json.encodeToString(elem),
            atTextElem = elem,
            textElem = TextElem(text),
            createTime = com.kurban.xuehuaim.sdk.util.System.currentTimeMillis(),
        )
    }

    suspend fun createMergerMessage(
        title: String,
        summaryList: List<String>,
        messageList: List<Message>
    ): Message {
        val sorted = messageList.sortedBy { it.sendTime ?: it.createTime ?: 0L }
        val mergeElem = MergeElem(
            title = title,
            abstractList = summaryList,
            multiMessage = sorted,
        )
        return Message(
            clientMsgID = ClientMsgIdGenerator.generate(),
            contentType = MessageType.MERGER,
            content = Json.encodeToString(mergeElem),
            mergeElem = mergeElem,
            createTime = com.kurban.xuehuaim.sdk.util.System.currentTimeMillis(),
        )
    }

    suspend fun sendMessage(
        message: Message,
        recvId: String,
        groupId: String = "",
        conversationId: String? = null,
        offlinePush: Boolean = true,
    ): Message =
        withContext(ioDispatcher) {
            val selfUserId = loginUserId.requireUserId()
            if (!isWebSocketConnected()) {
                throw XueHuaException.from(SdkErrorCode.WS_CONNECT_FAILED)
            }
            if (recvId.isBlank() && groupId.isBlank()) {
                throw XueHuaException.from(
                    SdkErrorCode.MSG_SEND_FAILED,
                    "recvID and groupID are both empty"
                )
            }
            val selfUser = databaseService.getAllUsers().find { it.userID == selfUserId }
            val sessionType = if (groupId.isNotEmpty()) {
                ConversationType.SUPER_GROUP.value
            } else {
                ConversationType.SINGLE.value
            }
            val sendingMessage = message.copy(
                sendID = selfUserId,
                recvID = recvId.takeIf { groupId.isBlank() && it.isNotBlank() },
                groupID = groupId.takeIf { it.isNotBlank() },
                sessionType = ConversationType.entries.find { it.value == sessionType },
                senderNickname = message.senderNickname ?: selfUser?.nickname,
                senderFaceUrl = message.senderFaceUrl ?: selfUser?.faceURL,
                platformID = currentPlatform().value,
                status = MessageStatus.SENDING,
                msgFrom = USER_MSG_FROM,
                conversationID = conversationId?.takeIf { it.isNotBlank() },
            ).withParsedContent()
            databaseService.insertOrReplaceMessage(sendingMessage)
            try {
                val reqData = SendMsgReqData(
                    sendID = selfUserId,
                    recvID = if (groupId.isNotEmpty()) "" else recvId,
                    groupID = groupId,
                    clientMsgID = message.clientMsgID,
                    senderPlatformID = currentPlatform().value,
                    senderNickname = selfUser?.nickname.orEmpty(),
                    senderFaceURL = selfUser?.faceURL.orEmpty(),
                    sessionType = sessionType,
                    msgFrom = USER_MSG_FROM,
                    contentType = message.contentType?.value ?: MessageType.TEXT.value,
                    contentBytes = (message.content ?: "").encodeToByteArray(),
                    createTime = message.createTime
                        ?: com.kurban.xuehuaim.sdk.util.System.currentTimeMillis(),
                    atUserIDList = message.atTextElem?.atUserList.orEmpty(),
                    offlinePush = offlinePush,
                )
                val response = webSocketService.sendRequest(
                    WsRequest(
                        reqIdentifier = WsIdentifier.SEND_MSG,
                        data = encodeSendMsgReq(reqData),
                    ),
                )
                if (!response.isSuccess) {
                    throw XueHuaException(
                        code = response.errCode,
                        message = response.errMsg.ifBlank { "message send failed" },
                    )
                }
                val respData = decodeUserSendMsgResp(response.data)
                    ?: throw XueHuaException.from(
                        SdkErrorCode.MSG_SEND_FAILED,
                        "invalid send response"
                    )
                val sent = sendingMessage.copy(
                    serverMsgID = respData.serverMsgID.takeIf { it.isNotBlank() },
                    clientMsgID = respData.clientMsgID.ifBlank { message.clientMsgID },
                    sendTime = respData.sendTime.takeIf { it > 0L },
                    status = MessageStatus.SEND_SUCCESS,
                ).withParsedContent()
                val enriched = MessageDisplayEnricher.enrichMessages(
                    apiService = apiService,
                    databaseService = databaseService,
                    messages = listOf(sent),
                ).first()
                databaseService.insertOrReplaceMessage(enriched)
                ConversationMessageUpdater.updateFromMessage(
                    databaseService = databaseService,
                    eventEmitter = eventEmitter,
                    message = enriched,
                    selfUserId = selfUserId,
                    isOutgoingSend = true,
                )
                com.kurban.xuehuaim.sdk.event.MessageEvent.SendSuccess(enriched)
                    .let { eventEmitter.emitMessage(it) }
                enriched
            } catch (e: Exception) {
                val failed = sendingMessage.copy(status = MessageStatus.SEND_FAILED)
                databaseService.insertOrReplaceMessage(failed)
                val code = (e as? XueHuaException)?.code ?: SdkErrorCode.MSG_SEND_FAILED.code
                val error = e.message ?: SdkErrorCode.MSG_SEND_FAILED.message
                eventEmitter.emitMessage(
                    com.kurban.xuehuaim.sdk.event.MessageEvent.SendFailed(
                        clientMsgId = message.clientMsgID,
                        code = code,
                        error = error,
                    ),
                )
                throw e
            }
        }

    suspend fun getAdvancedHistoryMessageList(
        conversationId: String,
        count: Int = 20
    ): List<Message> =
        fetchHistoryMessages(conversationId, count, startClientMsgId = null)

    suspend fun syncConversationMessages(conversationId: String, count: Int = 20) {
        getAdvancedHistoryMessageList(conversationId, count)
    }

    suspend fun getAdvancedHistoryMessageList(
        conversationId: String,
        count: Int,
        startClientMsgId: String?,
    ): List<Message> = fetchHistoryMessages(conversationId, count, startClientMsgId)

    private suspend fun fetchHistoryMessages(
        conversationId: String,
        count: Int,
        startClientMsgId: String?,
    ): List<Message> = withContext(ioDispatcher) {
        val queryCount = count.coerceAtLeast(1)
        val conv = databaseService.getConversation(conversationId) ?: return@withContext emptyList()
        val convMaxSeq = conv.maxSeq

        var localResult = loadLocalHistoryMessages(conversationId, queryCount, startClientMsgId)

        if (localResult.messages.size <= queryCount && isWebSocketConnected()) {
            val anchorSeq = resolveHistoryAnchorSeq(
                startClientMsgId = startClientMsgId,
                dataList = localResult.messages,
                convMaxSeq = convMaxSeq,
                startMsgSeq = localResult.startMsgSeq,
            )
            pullHistoryFromCloud(conversationId, queryCount, anchorSeq)
            localResult = loadLocalHistoryMessages(conversationId, queryCount, startClientMsgId)
        }

        val dataList = localResult.messages
        val result = if (dataList.size <= queryCount) dataList else dataList.take(queryCount)
        MessageDisplayEnricher.enrichMessages(apiService, databaseService, result)
    }

    private data class LocalHistoryResult(
        val messages: List<Message>,
        val startMsgSeq: Long? = null,
    )

    private suspend fun loadLocalHistoryMessages(
        conversationId: String,
        count: Int,
        startClientMsgId: String?,
    ): LocalHistoryResult {
        if (startClientMsgId.isNullOrBlank()) {
            return LocalHistoryResult(
                databaseService.getMessages(conversationId, (count + 1).toLong()),
            )
        }
        val all = databaseService.getMessages(conversationId, (count * 3).toLong())
        val index = all.indexOfFirst { it.clientMsgID == startClientMsgId }
        if (index < 0) {
            return LocalHistoryResult(emptyList())
        }
        val startMsgSeq = all[index].seq.takeIf { it > 0 }
        if (index == 0) {
            return LocalHistoryResult(emptyList(), startMsgSeq)
        }
        return LocalHistoryResult(
            messages = all.subList(maxOf(0, index - count), index),
            startMsgSeq = startMsgSeq,
        )
    }

    private fun resolveHistoryAnchorSeq(
        startClientMsgId: String?,
        dataList: List<Message>,
        convMaxSeq: Long,
        startMsgSeq: Long?,
    ): Long {
        if (!startClientMsgId.isNullOrBlank()) {
            val anchor = when {
                dataList.isNotEmpty() -> dataList.last().seq
                startMsgSeq != null && startMsgSeq > 0 -> startMsgSeq
                else -> convMaxSeq.coerceAtLeast(1L)
            }
            return if (anchor > 1) anchor else 1L
        }
        return when {
            dataList.isNotEmpty() -> dataList.last().seq.takeIf { it > 0 }
                ?: convMaxSeq.coerceAtLeast(1L)

            convMaxSeq > 0 -> convMaxSeq + 1
            else -> 1L
        }
    }

    private suspend fun pullHistoryFromCloud(conversationId: String, count: Int, currentSeq: Long) {
        if (currentSeq < 1) return
        val beginSeq = (currentSeq - count).coerceAtLeast(1L)
        val endSeq = currentSeq - 1
        if (endSeq < beginSeq) return
        msgSyncer.pullMessagesBySeqList(
            conversationId = conversationId,
            begin = beginSeq,
            end = endSeq,
            num = count.toLong(),
            order = 1,
        )
    }

    private fun isWebSocketConnected(): Boolean =
        eventEmitter.connectionState.value == ConnectionState.CONNECTED

    suspend fun revokeMessage(conversationId: String, clientMsgId: String) =
        withContext(ioDispatcher) {
            databaseService.deleteMessage(clientMsgId)
            eventEmitter.emitMessage(
                com.kurban.xuehuaim.sdk.event.MessageEvent.Revoked(
                    conversationId,
                    clientMsgId
                )
            )
        }

    suspend fun deleteMessageFromLocalStorage(conversationId: String, clientMsgId: String) =
        withContext(ioDispatcher) {
            databaseService.deleteMessage(clientMsgId)
            eventEmitter.emitMessage(
                com.kurban.xuehuaim.sdk.event.MessageEvent.Deleted(
                    conversationId,
                    clientMsgId
                )
            )
        }

    suspend fun markConversationMessageAsRead(conversationId: String) = withContext(ioDispatcher) {
        eventEmitter.emitMessage(
            com.kurban.xuehuaim.sdk.event.MessageEvent.ReadReceipt(conversationId, emptyList()),
        )
    }

    suspend fun searchLocalMessages(keyword: String, conversationId: String? = null): SearchResult {
        val messages = if (conversationId != null) {
            databaseService.getMessages(conversationId, 200)
        } else {
            emptyList()
        }
        return SearchResult(messageList = messages.filter { it.content?.contains(keyword) == true })
    }

    suspend fun sendSignalMessage(data: ByteArray): WsRequest =
        webSocketService.sendRequest(
            WsRequest(
                reqIdentifier = WsIdentifier.SEND_SIGNAL_MSG,
                data = data
            )
        ).let {
            WsRequest(reqIdentifier = it.reqIdentifier, data = it.data)
        }

    suspend fun sendRtcSignaling(
        toUserId: String,
        payload: String,
        isOnlineOnly: Boolean = true
    ): Message {
        val message = Message(
            clientMsgID = ClientMsgIdGenerator.generate(),
            contentType = MessageType.CUSTOM,
            content = payload,
            customElem = CustomElem(data = payload),
            createTime = com.kurban.xuehuaim.sdk.util.System.currentTimeMillis(),
        )
        return sendMessage(message, recvId = toUserId, offlinePush = !isOnlineOnly)
    }

    fun launchSendRtcSignaling(toUserId: String, payload: String, isOnlineOnly: Boolean) {
        scope.launch {
            runCatching { sendRtcSignaling(toUserId, payload, isOnlineOnly) }
        }
    }

    fun launchTimer(delayMs: Long, block: suspend () -> Unit): Job =
        scope.launch {
            delay(delayMs)
            block()
        }

    private companion object {
        const val USER_MSG_FROM = 100
    }
}

class ConversationManager internal constructor(
    private val apiService: ImApiService,
    private val databaseService: DatabaseService,
    private val eventEmitter: SdkEventEmitter,
    private val loginUserId: LoginUserIdProvider,
) {
    private val markingAsRead = mutableSetOf<String>()

    suspend fun getAllConversationList(): List<ConversationInfo> = withContext(ioDispatcher) {
        com.kurban.xuehuaim.sdk.util.ConversationSort.simpleSort(databaseService.getVisibleConversations())
    }

    suspend fun syncConversationsFromServer(): Int = withContext(ioDispatcher) {
        com.kurban.xuehuaim.sdk.sync.ConversationSync.syncFromServer(
            apiService = apiService,
            databaseService = databaseService,
            eventEmitter = eventEmitter,
            userId = loginUserId.requireUserId(),
        )
    }

    suspend fun getConversationListSplit(
        offset: Int = 0,
        count: Int = 100
    ): List<ConversationInfo> =
        withContext(ioDispatcher) {
            databaseService.getConversationsPage(offset, count)
        }

    suspend fun getConversation(conversationId: String): ConversationInfo? =
        withContext(ioDispatcher) {
            databaseService.getConversation(conversationId)
        }

    fun simpleSort(list: List<ConversationInfo>): List<ConversationInfo> =
        com.kurban.xuehuaim.sdk.util.ConversationSort.simpleSort(list)

    suspend fun pinConversation(conversationId: String, isPinned: Boolean) =
        withContext(ioDispatcher) {
            setConversation(conversationId, ConversationReq(isPinned = isPinned))
        }

    suspend fun setConversation(conversationId: String, req: ConversationReq) =
        withContext(ioDispatcher) {
            val target = databaseService.getConversation(conversationId) ?: return@withContext
            val updated = target.copy(
                isPinned = req.isPinned ?: target.isPinned,
                recvMsgOpt = req.recvMsgOpt?.let { v ->
                    ReceiveMessageOpt.entries.find { it.value == v }
                } ?: target.recvMsgOpt,
                isPrivateChat = req.isPrivateChat ?: target.isPrivateChat,
                ex = req.ex ?: target.ex,
                burnDuration = req.burnDuration ?: target.burnDuration,
                isMsgDestruct = req.isMsgDestruct ?: target.isMsgDestruct,
                msgDestructTime = req.msgDestructTime ?: target.msgDestructTime,
                groupAtType = req.groupAtType?.let { v ->
                    GroupAtType.entries.find { it.value == v }
                } ?: target.groupAtType,
            )
            databaseService.insertOrReplaceConversation(updated)
            notifyConversationChanged(conversationId)
            apiService.setConversations(loginUserId.requireUserId(), conversationId, req)
        }

    suspend fun setConversationDraft(conversationId: String, draftText: String) =
        withContext(ioDispatcher) {
            val target = databaseService.getConversation(conversationId) ?: return@withContext
            val updated = target.copy(
                draftText = draftText,
                draftTextTime = com.kurban.xuehuaim.sdk.util.System.currentTimeMillis(),
            )
            databaseService.insertOrReplaceConversation(updated)
            notifyConversationChanged(conversationId)
        }

    suspend fun hideConversation(conversationId: String) = withContext(ioDispatcher) {
        databaseService.resetConversation(conversationId)
        notifyConversationChanged(conversationId)
    }

    suspend fun deleteConversationAndDeleteAllMsg(conversationId: String) =
        withContext(ioDispatcher) {
            val conv = databaseService.getConversation(conversationId)
                ?: throw XueHuaException.from(
                    SdkErrorCode.PARAM_ERROR,
                    "conversation not found: $conversationId"
                )
            apiService.clearConversationMsg(loginUserId.requireUserId(), listOf(conversationId))
            databaseService.deleteChatLogsByConversation(conversationId)
            val hasReadSeq = if (conv.maxSeq > 0) conv.maxSeq else conv.hasReadSeq
            if (hasReadSeq > 0) {
                databaseService.updateConversationUnread(conversationId, 0, hasReadSeq)
            }
            databaseService.resetConversation(conversationId)
            notifyConversationChanged(conversationId)
            emitTotalUnread()
        }

    suspend fun markConversationMessageAsRead(conversationId: String) = withContext(ioDispatcher) {
        if (markingAsRead.contains(conversationId)) {
            emitTotalUnread()
            return@withContext
        }
        val conv = databaseService.getConversation(conversationId) ?: run {
            emitTotalUnread()
            return@withContext
        }
        if (conv.unreadCount == 0) return@withContext
        markingAsRead.add(conversationId)
        try {
            var hasReadSeq = conv.maxSeq
            if (hasReadSeq <= 0) {
                val messages = databaseService.getMessages(conversationId, 1)
                hasReadSeq = messages.firstOrNull()?.seq ?: conv.hasReadSeq
            }
            databaseService.updateConversationUnread(conversationId, 0, hasReadSeq)
            notifyConversationChanged(conversationId)
            apiService.markConversationAsRead(
                loginUserId.requireUserId(),
                conversationId,
                hasReadSeq,
            )
        } finally {
            markingAsRead.remove(conversationId)
        }
    }

    suspend fun getTotalUnreadMsgCount(): Int = withContext(ioDispatcher) {
        databaseService.getTotalUnreadCount()
    }

    private suspend fun notifyConversationChanged(conversationId: String) {
        val conv = databaseService.getConversation(conversationId)
        if (conv != null && (conv.latestMsgSendTime ?: 0) > 0) {
            eventEmitter.emitConversation(
                com.kurban.xuehuaim.sdk.event.ConversationEvent.Changed(
                    conv
                )
            )
        }
        emitTotalUnread()
    }

    private suspend fun emitTotalUnread() {
        val total = databaseService.getTotalUnreadCount()
        eventEmitter.emitConversation(
            com.kurban.xuehuaim.sdk.event.ConversationEvent.TotalUnreadChanged(total),
        )
    }
}

class FriendshipManager internal constructor(
    private val apiService: ImApiService,
    private val eventEmitter: SdkEventEmitter,
    private val loginUserId: LoginUserIdProvider,
) {
    suspend fun getFriendList(): List<FriendInfo> =
        apiService.getFriendList(loginUserId.requireUserId())

    suspend fun getFriendListPage(pageNumber: Int, pageSize: Int): List<FriendInfo> =
        apiService.getFriendListPage(loginUserId.requireUserId(), pageNumber, pageSize)

    suspend fun addFriend(userId: String, reqMsg: String = "") = withContext(ioDispatcher) {
        apiService.addFriendRequest(userId, reqMsg)
    }

    suspend fun deleteFriend(userId: String) = withContext(ioDispatcher) {
        apiService.deleteFriend(loginUserId.requireUserId(), userId)
        eventEmitter.emitFriendship(
            com.kurban.xuehuaim.sdk.event.FriendshipEvent.FriendDeleted(
                userId
            )
        )
    }

    suspend fun getFriendApplications(): List<FriendApplicationInfo> =
        apiService.getFriendApplications(loginUserId.requireUserId())

    suspend fun respondFriendApplication(
        toUserID: String,
        accept: Boolean,
        handleMsg: String = ""
    ) =
        withContext(ioDispatcher) {
            apiService.respondFriendApplication(toUserID, accept, handleMsg)
        }

    suspend fun getBlackList(): List<BlacklistInfo> =
        apiService.getBlackList(loginUserId.requireUserId())

    suspend fun addBlack(userID: String) = withContext(ioDispatcher) {
        apiService.addBlack(loginUserId.requireUserId(), userID)
    }

    suspend fun removeBlack(userID: String) = withContext(ioDispatcher) {
        apiService.removeBlack(loginUserId.requireUserId(), userID)
    }

    suspend fun searchUsers(keyword: String): List<UserInfo> = apiService.searchUsers(keyword)
}

class GroupManager internal constructor(
    private val apiService: ImApiService,
    private val eventEmitter: SdkEventEmitter,
    private val loginUserId: LoginUserIdProvider,
) {
    suspend fun getJoinedGroupList(): List<GroupInfo> =
        apiService.getJoinedGroupList(loginUserId.requireUserId())

    suspend fun createGroup(groupName: String, memberUserIds: List<String>): GroupInfo =
        withContext(ioDispatcher) {
            val group = apiService.createGroup(groupName, memberUserIds)
            eventEmitter.emitGroup(com.kurban.xuehuaim.sdk.event.GroupEvent.GroupInfoChanged(group))
            group
        }

    suspend fun dismissGroup(groupId: String) = withContext(ioDispatcher) {
        apiService.dismissGroup(groupId)
        eventEmitter.emitGroup(com.kurban.xuehuaim.sdk.event.GroupEvent.GroupDismissed(groupId))
    }

    suspend fun quitGroup(groupId: String) = withContext(ioDispatcher) {
        val userId = loginUserId.requireUserId()
        apiService.quitGroup(userId, groupId)
        eventEmitter.emitGroup(com.kurban.xuehuaim.sdk.event.GroupEvent.GroupDismissed(groupId))
    }

    suspend fun getGroupsInfo(groupIds: List<String>): List<GroupInfo> =
        apiService.getGroupsInfo(groupIds)

    suspend fun setGroupInfo(groupInfo: GroupInfo) = withContext(ioDispatcher) {
        apiService.setGroupInfoEx(groupInfo)
        eventEmitter.emitGroup(com.kurban.xuehuaim.sdk.event.GroupEvent.GroupInfoChanged(groupInfo))
    }

    suspend fun getGroupMembers(groupId: String): List<GroupMemberInfo> =
        apiService.getGroupMembers(groupId)

    suspend fun inviteToGroup(groupId: String, userIds: List<String>) = withContext(ioDispatcher) {
        apiService.inviteToGroup(groupId, userIds)
    }

    suspend fun kickGroupMember(groupId: String, userId: String) = withContext(ioDispatcher) {
        apiService.kickGroupMember(groupId, userId)
    }

    suspend fun getGroupApplicationListAsRecipient(
        pageNumber: Int = 1,
        pageSize: Int = 100,
    ): List<GroupApplicationInfo> =
        apiService.getRecvGroupApplicationList(loginUserId.requireUserId(), pageNumber, pageSize)

    suspend fun getGroupApplicationListAsApplicant(
        pageNumber: Int = 1,
        pageSize: Int = 100,
    ): List<GroupApplicationInfo> =
        apiService.getSendGroupApplicationList(loginUserId.requireUserId(), pageNumber, pageSize)
}

class UserManager internal constructor(
    private val apiService: ImApiService,
    private val databaseService: DatabaseService,
    private val eventEmitter: SdkEventEmitter,
    private val loginUserId: LoginUserIdProvider,
) {
    suspend fun getUsersInfo(userIds: List<String>): List<UserInfo> {
        val users = apiService.getUsersInfo(userIds)
        users.forEach { databaseService.insertOrReplaceUser(it) }
        return users
    }

    suspend fun getUserFullInfo(userID: String): UserFullInfo? = withContext(ioDispatcher) {
        apiService.getUserFullInfo(listOf(userID)).firstOrNull()
    }

    suspend fun updateChatUserInfo(
        nickname: String? = null,
        faceURL: String? = null,
        email: String? = null,
        phoneNumber: String? = null,
        areaCode: String? = null,
        gender: Int? = null,
        birth: Long? = null,
        account: String? = null,
    ) = withContext(ioDispatcher) {
        val userID = loginUserId.requireUserId()
        apiService.updateChatUserInfo(
            com.kurban.xuehuaim.sdk.network.http.UpdateChatUserInfoReq(
                userID = userID,
                nickname = nickname,
                faceURL = faceURL,
                email = email,
                phoneNumber = phoneNumber,
                areaCode = areaCode,
                gender = gender,
                birth = birth,
                account = account,
            ),
        )
        val updated = getUsersInfo(listOf(userID)).firstOrNull()
        if (updated != null) {
            eventEmitter.emitUser(com.kurban.xuehuaim.sdk.event.UserEvent.SelfInfoUpdated(updated))
        }
    }

    suspend fun checkPaymentPasswordSet(): Boolean = withContext(ioDispatcher) {
        apiService.checkPaymentPasswordSet()
    }

    suspend fun setPaymentPassword(paymentPassword: String, loginPassword: String) =
        withContext(ioDispatcher) {
            apiService.setPaymentPassword(paymentPassword, loginPassword)
        }

    suspend fun changePaymentPassword(currentPaymentPassword: String, newPaymentPassword: String) =
        withContext(ioDispatcher) {
            apiService.changePaymentPassword(currentPaymentPassword, newPaymentPassword)
        }

    suspend fun verifyPaymentPassword(paymentPassword: String) = withContext(ioDispatcher) {
        apiService.verifyPaymentPassword(paymentPassword)
    }

    suspend fun setSelfInfo(nickname: String?, faceUrl: String?) = withContext(ioDispatcher) {
        updateChatUserInfo(nickname = nickname, faceURL = faceUrl)
    }

    suspend fun sendVerificationCode(
        usedFor: Int,
        areaCode: String? = null,
        phoneNumber: String? = null,
        email: String? = null,
        invitationCode: String? = null,
    ) = withContext(ioDispatcher) {
        apiService.sendVerificationCode(
            com.kurban.xuehuaim.sdk.network.http.SendVerifyCodeReq(
                areaCode = areaCode,
                phoneNumber = phoneNumber,
                email = email,
                usedFor = usedFor,
                invitationCode = invitationCode,
            ),
        )
    }

    suspend fun register(
        nickname: String,
        password: String,
        verificationCode: String,
        deviceID: String,
        platform: Int,
        email: String? = null,
        phoneNumber: String? = null,
        areaCode: String? = null,
        account: String? = null,
        faceURL: String? = null,
        birth: Long = 0,
        gender: Int = 1,
        invitationCode: String? = null,
        autoLogin: Boolean = true,
    ): AuthCacheData? = withContext(ioDispatcher) {
        val resp = apiService.register(
            com.kurban.xuehuaim.sdk.network.http.RegisterReq(
                deviceID = deviceID,
                verifyCode = verificationCode,
                platform = platform,
                invitationCode = invitationCode,
                autoLogin = autoLogin,
                user = com.kurban.xuehuaim.sdk.network.http.RegisterUserInfo(
                    nickname = nickname,
                    faceURL = faceURL,
                    birth = birth,
                    gender = gender,
                    email = email,
                    areaCode = areaCode,
                    phoneNumber = phoneNumber,
                    account = account,
                    password = md5Hex(password),
                ),
            ),
        )
        AuthCacheData(
            userID = resp.userID,
            imToken = resp.imToken ?: resp.chatToken,
            chatToken = resp.chatToken,
            nickname = resp.nickname,
            faceURL = resp.faceURL,
        )
    }

    suspend fun resetPassword(
        verifyCode: String,
        newPassword: String,
        areaCode: String? = null,
        phoneNumber: String? = null,
        email: String? = null,
    ) = withContext(ioDispatcher) {
        apiService.resetPassword(
            com.kurban.xuehuaim.sdk.network.http.ResetPasswordReq(
                areaCode = areaCode,
                phoneNumber = phoneNumber,
                email = email,
                verifyCode = verifyCode,
                password = md5Hex(newPassword),
            ),
        )
    }

    suspend fun changePassword(userID: String, currentPassword: String, newPassword: String) =
        withContext(ioDispatcher) {
            apiService.changePassword(userID, currentPassword, newPassword)
        }
}

class MomentsManager internal constructor(
    private val apiService: ImApiService,
    private val eventEmitter: SdkEventEmitter,
    private val loginUserId: LoginUserIdProvider,
) {
    suspend fun createMoment(content: String): MomentInfo {
        val moment = apiService.createMoment(content = content)
        eventEmitter.emitMoments(com.kurban.xuehuaim.sdk.event.MomentsEvent.NewMoment(moment))
        return moment
    }

    suspend fun deleteMoment(momentId: String) = withContext(ioDispatcher) {
        apiService.deleteMoment(momentId)
        eventEmitter.emitMoments(com.kurban.xuehuaim.sdk.event.MomentsEvent.MomentDeleted(momentId))
    }

    suspend fun getMomentsList(page: Int = 1, size: Int = 20): List<MomentInfo> =
        apiService.getMomentsList(
            ownerUserID = "",
            pageNumber = page,
            showNumber = size,
        )

    suspend fun likeMoment(momentId: String) = withContext(ioDispatcher) {
        apiService.likeMoment(momentId, ownerUserID = loginUserId())
    }

    suspend fun commentMoment(momentId: String, content: String) = withContext(ioDispatcher) {
        apiService.commentMoment(momentId, content, ownerUserID = loginUserId())
    }

    suspend fun unlikeMoment(momentId: String, ownerUserID: String? = null) =
        withContext(ioDispatcher) {
            apiService.unlikeMoment(momentId, ownerUserID = ownerUserID ?: loginUserId())
            eventEmitter.emitMoments(
                com.kurban.xuehuaim.sdk.event.MomentsEvent.Unliked(
                    momentId,
                    loginUserId().orEmpty()
                ),
            )
        }

    suspend fun deleteComment(momentId: String, commentId: String) = withContext(ioDispatcher) {
        apiService.deleteMomentComment(commentId)
        eventEmitter.emitMoments(
            com.kurban.xuehuaim.sdk.event.MomentsEvent.CommentDeleted(momentId, commentId),
        )
    }
}

class FavoriteManager internal constructor(
    private val apiService: ImApiService,
    private val eventEmitter: SdkEventEmitter,
) {
    suspend fun getFavoriteList(): List<FavoriteItem> = apiService.getFavoriteList()

    suspend fun addFavorite(item: FavoriteItem) = withContext(ioDispatcher) {
        val saved = apiService.addFavorite(item)
        eventEmitter.emitFavorite(com.kurban.xuehuaim.sdk.event.FavoriteEvent.Added(saved))
        saved
    }

    suspend fun deleteFavorite(favoriteId: String) = withContext(ioDispatcher) {
        apiService.deleteFavorite(favoriteId)
        eventEmitter.emitFavorite(com.kurban.xuehuaim.sdk.event.FavoriteEvent.Deleted(favoriteId))
    }
}

class RedPacketManager internal constructor(
    private val apiService: ImApiService,
    private val eventEmitter: SdkEventEmitter,
) {
    suspend fun getPointsBalance(): Double = withContext(ioDispatcher) {
        apiService.getPointsBalance()
    }

    suspend fun getPointsTransactions(
        pageNumber: Int = 1,
        showNumber: Int = 20,
        txType: Int? = null,
    ): Pair<Int, List<PointsTransaction>> = withContext(ioDispatcher) {
        apiService.getPointsTransactions(pageNumber, showNumber, txType)
    }

    suspend fun sendRedPacket(title: String, amount: Double, count: Int): RedPacketInfo =
        withContext(ioDispatcher) {
            val packet = RedPacketInfo(
                packetID = ClientMsgIdGenerator.generate(),
                senderUserID = "",
                title = title,
                amount = amount,
                count = count,
            )
            eventEmitter.emitRedPacket(com.kurban.xuehuaim.sdk.event.RedPacketEvent.Received(packet))
            packet
        }

    suspend fun grabRedPacket(packetId: String): Double = withContext(ioDispatcher) {
        eventEmitter.emitRedPacket(
            com.kurban.xuehuaim.sdk.event.RedPacketEvent.Grabbed(
                packetId,
                0.0
            )
        )
        0.0
    }
}

class ReportAppealManager internal constructor(
    private val apiService: ImApiService,
) {
    suspend fun submitReport(report: ReportInfo): ReportInfo = apiService.submitReport(report)
    suspend fun submitAppeal(appeal: AppealInfo): AppealInfo = apiService.submitAppeal(appeal)
}

class ApplicationManager internal constructor(
    private val apiService: ImApiService,
) {
    suspend fun checkVersion(): ApplicationVersionInfo = apiService.checkAppVersion()

    suspend fun changePassword(userID: String, currentPassword: String, newPassword: String) =
        apiService.changePassword(userID, currentPassword, newPassword)

    suspend fun deleteAccount(currentPassword: String) = apiService.deleteAccount(currentPassword)

    suspend fun getPointsBalance(): Double = apiService.getPointsBalance()

    suspend fun getPointsTransactions(
        pageNumber: Int = 1,
        showNumber: Int = 20,
        txType: Int? = null,
    ): Pair<Int, List<PointsTransaction>> =
        apiService.getPointsTransactions(pageNumber, showNumber, txType)
}
