package com.kurban.xuehuaim.sdk.manager

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.db.SendingMessage
import com.kurban.xuehuaim.sdk.enum.ConversationType
import com.kurban.xuehuaim.sdk.enum.ConnectionState
import com.kurban.xuehuaim.sdk.enum.MessageStatus
import com.kurban.xuehuaim.sdk.enum.MessageType
import com.kurban.xuehuaim.sdk.enum.SdkErrorCode
import com.kurban.xuehuaim.sdk.exception.XueHuaException
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.model.AdvancedMessage
import com.kurban.xuehuaim.sdk.model.AtTextElem
import com.kurban.xuehuaim.sdk.model.AtUserInfo
import com.kurban.xuehuaim.sdk.model.CustomElem
import com.kurban.xuehuaim.sdk.model.FileElem
import com.kurban.xuehuaim.sdk.model.MergeElem
import com.kurban.xuehuaim.sdk.model.Message
import com.kurban.xuehuaim.sdk.model.PictureElem
import com.kurban.xuehuaim.sdk.model.PictureInfo
import com.kurban.xuehuaim.sdk.model.QuoteElem
import com.kurban.xuehuaim.sdk.model.SearchParams
import com.kurban.xuehuaim.sdk.model.SearchResult
import com.kurban.xuehuaim.sdk.model.SoundElem
import com.kurban.xuehuaim.sdk.model.TextElem
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
import com.kurban.xuehuaim.sdk.platform.FileSystem
import com.kurban.xuehuaim.sdk.platform.currentPlatform
import com.kurban.xuehuaim.sdk.platform.ioDispatcher
import com.kurban.xuehuaim.sdk.sync.MessageDisplayEnricher
import com.kurban.xuehuaim.sdk.sync.MessageGapChecker
import com.kurban.xuehuaim.sdk.util.ClientMsgIdGenerator
import com.kurban.xuehuaim.sdk.util.ConversationMessageUpdater
import com.kurban.xuehuaim.sdk.util.OpenImUtils
import com.kurban.xuehuaim.sdk.util.withParsedContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds


class MessageManager internal constructor(
    private val apiService: ImApiService,
    private val databaseService: DatabaseService,
    private val webSocketService: WebSocketService,
    private val msgSyncer: MsgSyncer,
    private val notificationDispatcher: NotificationDispatcher,
    private val eventEmitter: SdkEventEmitter,
    private val loginUserId: LoginUserIdProvider,
    private val fileUploadService: FileUploadService,
    private val fileSystem: FileSystem,
) {
    private var conversationManager: ConversationManager? = null
    private val messageGapChecker by lazy {
        MessageGapChecker(databaseService) { conversationId, lostSeqs, isReverse ->
            msgSyncer.pullMessagesByLostSeqs(conversationId, lostSeqs, isReverse = isReverse)
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    internal val pendingUploadBytes = mutableMapOf<String, ByteArray>()
    internal val pendingSnapshotBytes = mutableMapOf<String, ByteArray>()

    internal fun bindConversationManager(manager: ConversationManager) {
        conversationManager = manager
    }

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

    suspend fun createCustomMessage(
        data: String,
        extension: String = "",
        description: String = "",
    ): Message {
        val elem = CustomElem(data = data, extension = extension, description = description)
        return Message(
            clientMsgID = ClientMsgIdGenerator.generate(),
            contentType = MessageType.CUSTOM,
            content = Json.encodeToString(elem),
            customElem = elem,
            createTime = com.kurban.xuehuaim.sdk.util.System.currentTimeMillis(),
        )
    }

    suspend fun createForwardMessage(message: Message): Message {
        val selfUserId = loginUserId()
        return message.copy(
            clientMsgID = ClientMsgIdGenerator.generate(),
            createTime = com.kurban.xuehuaim.sdk.util.System.currentTimeMillis(),
            sendTime = 0,
            status = MessageStatus.SENDING,
            sendID = selfUserId,
        ).withParsedContent()
    }

    suspend fun createFaceMessage(index: Int = -1, data: String? = null): Message {
        val payload = """{"index":$index,"data":"${data.orEmpty()}"}"""
        return Message(
            clientMsgID = ClientMsgIdGenerator.generate(),
            contentType = MessageType.CUSTOM_FACE,
            content = payload,
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
            val existing = databaseService.getMessageByClientMsgId(message.clientMsgID)
            if (existing != null) {
                val status = existing.status
                when (status) {
                    MessageStatus.SEND_SUCCESS ->
                        throw XueHuaException.from(
                            SdkErrorCode.MSG_SEND_FAILED,
                            "Message is repeated"
                        )

                    MessageStatus.SEND_FAILED, MessageStatus.SENDING -> Unit
                    else -> throw XueHuaException.from(
                        SdkErrorCode.MSG_SEND_FAILED,
                        "Message is repeated"
                    )
                }
            }
            val uploadedMessage = handleMediaUploadIfNeeded(
                fileUploadService = fileUploadService,
                fileSystem = fileSystem,
                pendingUploadBytes = pendingUploadBytes,
                pendingSnapshotBytes = pendingSnapshotBytes,
                message = message,
            )
            val selfUser = databaseService.getAllUsers().find { it.userID == selfUserId }
            val sessionType = if (groupId.isNotEmpty()) {
                ConversationType.SUPER_GROUP.value
            } else {
                ConversationType.SINGLE.value
            }
            val sendingMessage = uploadedMessage.copy(
                sendID = selfUserId,
                recvID = recvId.takeIf { groupId.isBlank() && it.isNotBlank() },
                groupID = groupId.takeIf { it.isNotBlank() },
                sessionType = ConversationType.entries.find { it.value == sessionType },
                senderNickname = uploadedMessage.senderNickname ?: selfUser?.nickname,
                senderFaceUrl = uploadedMessage.senderFaceUrl ?: selfUser?.faceURL,
                platformID = currentPlatform().value,
                status = MessageStatus.SENDING,
                msgFrom = USER_MSG_FROM,
                conversationID = conversationId?.takeIf { it.isNotBlank() },
            ).withParsedContent()
            databaseService.insertOrReplaceMessage(sendingMessage)
            val resolvedConversationId = conversationId?.takeIf { it.isNotBlank() }
                ?: sendingMessage.conversationID.orEmpty().ifBlank {
                    if (groupId.isNotEmpty()) {
                        OpenImUtils.genGroupConversationID(groupId)
                    } else {
                        OpenImUtils.genSingleConversationID(selfUserId, recvId)
                    }
                }
            databaseService.insertOrReplaceSendingMessage(
                SendingMessage(
                    clientMsgID = sendingMessage.clientMsgID,
                    conversationID = resolvedConversationId,
                ),
            )
            eventEmitter.emitMessage(
                com.kurban.xuehuaim.sdk.event.MessageEvent.SendProgress(
                    clientMsgId = sendingMessage.clientMsgID,
                    progress = 0,
                ),
            )
            try {
                val reqData = SendMsgReqData(
                    sendID = selfUserId,
                    recvID = if (groupId.isNotEmpty()) "" else recvId,
                    groupID = groupId,
                    clientMsgID = sendingMessage.clientMsgID,
                    senderPlatformID = currentPlatform().value,
                    senderNickname = selfUser?.nickname.orEmpty(),
                    senderFaceURL = selfUser?.faceURL.orEmpty(),
                    sessionType = sessionType,
                    msgFrom = USER_MSG_FROM,
                    contentType = sendingMessage.contentType?.value ?: MessageType.TEXT.value,
                    contentBytes = (sendingMessage.content ?: "").encodeToByteArray(),
                    createTime = sendingMessage.createTime
                        ?: com.kurban.xuehuaim.sdk.util.System.currentTimeMillis(),
                    atUserIDList = sendingMessage.atTextElem?.atUserList.orEmpty(),
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
                    clientMsgID = respData.clientMsgID.ifBlank { sendingMessage.clientMsgID },
                    sendTime = respData.sendTime.takeIf { it > 0L },
                    status = MessageStatus.SEND_SUCCESS,
                ).withParsedContent()
                val enriched = MessageDisplayEnricher.enrichMessages(
                    apiService = apiService,
                    databaseService = databaseService,
                    messages = listOf(sent),
                ).first()
                databaseService.insertOrReplaceMessage(enriched)
                databaseService.deleteSendingMessage(sendingMessage.clientMsgID)
                ConversationMessageUpdater.updateFromMessage(
                    databaseService = databaseService,
                    eventEmitter = eventEmitter,
                    message = enriched,
                    selfUserId = selfUserId,
                    isOutgoingSend = true,
                )
                com.kurban.xuehuaim.sdk.event.MessageEvent.SendSuccess(enriched)
                    .let { eventEmitter.emitMessage(it) }
                eventEmitter.emitMessage(
                    com.kurban.xuehuaim.sdk.event.MessageEvent.SendProgress(
                        clientMsgId = enriched.clientMsgID,
                        progress = 100,
                    ),
                )
                enriched
            } catch (e: Exception) {
                val failed = sendingMessage.copy(status = MessageStatus.SEND_FAILED)
                databaseService.insertOrReplaceMessage(failed)
                databaseService.deleteSendingMessage(sendingMessage.clientMsgID)
                val code = (e as? XueHuaException)?.code ?: SdkErrorCode.MSG_SEND_FAILED.code
                val error = e.message ?: SdkErrorCode.MSG_SEND_FAILED.message
                eventEmitter.emitMessage(
                    com.kurban.xuehuaim.sdk.event.MessageEvent.SendFailed(
                        clientMsgId = sendingMessage.clientMsgID,
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
    ): AdvancedMessage =
        fetchHistoryMessages(conversationId, count, startClientMsgId = null, isReverse = false)

    suspend fun syncConversationMessages(conversationId: String, count: Int = 20) {
        getAdvancedHistoryMessageList(conversationId, count)
    }

    suspend fun getAdvancedHistoryMessageList(
        conversationId: String,
        count: Int,
        startClientMsgId: String?,
    ): AdvancedMessage = fetchHistoryMessages(conversationId, count, startClientMsgId, isReverse = false)

    internal suspend fun getAdvancedHistoryMessageListInternal(
        conversationId: String,
        count: Int,
        startClientMsgId: String?,
        isReverse: Boolean,
    ): AdvancedMessage = fetchHistoryMessages(conversationId, count, startClientMsgId, isReverse)

    private suspend fun fetchHistoryMessages(
        conversationId: String,
        count: Int,
        startClientMsgId: String?,
        isReverse: Boolean,
    ): AdvancedMessage = withContext(ioDispatcher) {
        val gapResult = messageGapChecker.fetchMessagesWithGapCheck(
            conversationId = conversationId,
            count = count,
            startClientMsgId = startClientMsgId,
            isReverse = isReverse,
        )
        val enriched = MessageDisplayEnricher.enrichMessages(
            apiService,
            databaseService,
            gapResult.messages,
        )
        AdvancedMessage(
            messageList = enriched,
            isEnd = gapResult.isEnd,
            lastMinSeq = gapResult.lastMinSeq,
        )
    }

    private fun isWebSocketConnected(): Boolean =
        eventEmitter.connectionState.value == ConnectionState.CONNECTED

    suspend fun revokeMessage(conversationId: String, clientMsgId: String) =
        withContext(ioDispatcher) {
            val msg = databaseService.getMessageByClientMsgId(clientMsgId)
            val seq = msg?.seq ?: 0L
            databaseService.updateMessageContentType(clientMsgId, MessageType.MSG_REVOKE.value)
            eventEmitter.emitMessage(
                com.kurban.xuehuaim.sdk.event.MessageEvent.Revoked(conversationId, clientMsgId),
            )
            ConversationLatestMsgHelper.updateConversationIfLatestMsg(
                databaseService = databaseService,
                eventEmitter = eventEmitter,
                conversationId = conversationId,
                clientMsgId = clientMsgId,
            )
            if (seq > 0) {
                runCatching {
                    apiService.revokeMsg(
                        loginUserId.requireUserId(),
                        conversationId,
                        seq,
                    )
                }
            }
        }

    suspend fun deleteMessageFromLocalStorage(conversationId: String, clientMsgId: String) =
        withContext(ioDispatcher) {
            databaseService.deleteMessage(clientMsgId)
            ConversationLatestMsgHelper.updateConversationIfLatestMsg(
                databaseService = databaseService,
                eventEmitter = eventEmitter,
                conversationId = conversationId,
                clientMsgId = clientMsgId,
            )
            eventEmitter.emitMessage(
                com.kurban.xuehuaim.sdk.event.MessageEvent.Deleted(
                    conversationId,
                    clientMsgId
                )
            )
        }

    suspend fun deleteMessageFromLocalAndSvr(conversationId: String, clientMsgId: String) =
        withContext(ioDispatcher) {
            val msg = databaseService.getMessageByClientMsgId(clientMsgId)
            val seq = msg?.seq ?: 0L
            deleteMessageFromLocalStorage(conversationId, clientMsgId)
            if (seq > 0) {
                runCatching {
                    apiService.deleteMsgs(
                        loginUserId.requireUserId(),
                        conversationId,
                        listOf(seq),
                    )
                }
            }
        }

    suspend fun deleteAllMsgFromLocal() = withContext(ioDispatcher) {
        val affected = databaseService.getAllConversations().map { it.conversationID }
        databaseService.deleteAllChatLogs()
        if (affected.isNotEmpty()) {
            val convList = databaseService.getMultipleConversations(affected)
            convList.forEach { conv ->
                eventEmitter.emitConversation(
                    com.kurban.xuehuaim.sdk.event.ConversationEvent.Changed(
                        conv
                    )
                )
            }
        }
        eventEmitter.emitConversation(
            com.kurban.xuehuaim.sdk.event.ConversationEvent.TotalUnreadChanged(
                databaseService.getTotalUnreadCount(),
            ),
        )
    }

    suspend fun deleteAllMsgFromLocalAndSvr() = withContext(ioDispatcher) {
        deleteAllMsgFromLocal()
        runCatching { apiService.clearAllMsg(loginUserId.requireUserId()) }
    }

    suspend fun setMessageLocalEx(conversationId: String, clientMsgId: String, localEx: String) =
        withContext(ioDispatcher) {
            databaseService.updateMessageLocalEx(clientMsgId, localEx)
            val msg = databaseService.getMessageByClientMsgId(clientMsgId) ?: return@withContext
            ConversationLatestMsgHelper.updateLatestMsgFromMessage(
                databaseService = databaseService,
                eventEmitter = eventEmitter,
                conversationId = conversationId,
                message = msg.copy(localEx = localEx),
            )
        }

    suspend fun findMessageList(searchParams: List<SearchParams>): SearchResult {
        val messages = searchParams.flatMap { param ->
            val ids = param.clientMsgIDList.orEmpty()
            if (ids.isEmpty()) emptyList() else databaseService.getMessagesByClientMsgIds(ids)
        }
        return SearchResult(messageList = messages)
    }

    suspend fun markConversationMessageAsRead(conversationId: String) =
        conversationManager?.markConversationMessageAsRead(conversationId)
            ?: withContext(ioDispatcher) {
                eventEmitter.emitMessage(
                    com.kurban.xuehuaim.sdk.event.MessageEvent.ReadReceipt(
                        conversationId,
                        emptyList()
                    ),
                )
            }

    suspend fun searchLocalMessages(
        keyword: String,
        conversationId: String? = null,
        messageTypeList: List<Int> = emptyList(),
        pageIndex: Int = 1,
        count: Int = 40,
    ): SearchResult {
        val offset = (pageIndex - 1) * count
        val messages = databaseService.searchMessages(
            conversationId = conversationId,
            keyword = keyword.takeIf { it.isNotBlank() },
            messageTypes = messageTypeList.takeIf { it.isNotEmpty() },
            offset = offset,
            count = count,
        )
        return SearchResult(messageList = messages)
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
            delay(delayMs.milliseconds)
            block()
        }

    suspend fun createImageMessageFromFullPath(path: String): Message =
        createImageMessageFromFullPath(fileSystem, path)

    suspend fun createImageMessageFromBytes(bytes: ByteArray, fileName: String): Message =
        createImageMessageFromBytes(this, bytes, fileName)

    suspend fun createSoundMessageFromFullPath(path: String, duration: Long): Message =
        createSoundMessageFromFullPath(fileSystem, path, duration)

    suspend fun createVideoMessageFromFullPath(
        path: String,
        duration: Long,
        snapshotPath: String? = null,
    ): Message = createVideoMessageFromFullPath(fileSystem, path, duration, snapshotPath)

    suspend fun createFileMessageFromFullPath(path: String): Message =
        createFileMessageFromFullPath(fileSystem, path)

    suspend fun createVideoMessageFromBytes(
        bytes: ByteArray,
        fileName: String,
        duration: Int,
        videoType: String? = null,
        snapshotBytes: ByteArray? = null,
    ): Message =
        createVideoMessageFromBytes(this, bytes, fileName, duration, videoType, snapshotBytes)

    suspend fun createFileMessageFromBytes(bytes: ByteArray, fileName: String): Message =
        createFileMessageFromBytes(this, bytes, fileName)

    suspend fun recoverSendingMessages() =
        recoverSendingMessages(databaseService, eventEmitter, loginUserId)

    suspend fun insertSingleMessageToLocalStorage(
        receiverId: String,
        senderId: String,
        message: Message? = null,
    ): Message = insertSingleMessageToLocalStorage(
        databaseService,
        eventEmitter,
        loginUserId,
        receiverId,
        senderId,
        message,
    )

    suspend fun insertGroupMessageToLocalStorage(
        groupId: String,
        senderId: String,
        message: Message? = null,
    ): Message = insertGroupMessageToLocalStorage(
        databaseService,
        eventEmitter,
        loginUserId,
        groupId,
        senderId,
        message,
    )

    private companion object {
        const val USER_MSG_FROM = 100
    }
}
