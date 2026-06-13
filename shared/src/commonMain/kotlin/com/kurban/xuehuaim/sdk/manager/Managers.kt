package com.kurban.xuehuaim.sdk.manager

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.db.SendingMessage
import com.kurban.xuehuaim.sdk.enum.FavoriteType
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
import com.kurban.xuehuaim.sdk.model.MomentCommentWithUser
import com.kurban.xuehuaim.sdk.model.MomentInfo
import com.kurban.xuehuaim.sdk.model.MomentListResponse
import com.kurban.xuehuaim.sdk.model.PictureElem
import com.kurban.xuehuaim.sdk.model.PictureInfo
import com.kurban.xuehuaim.sdk.model.PointsTransaction
import com.kurban.xuehuaim.sdk.model.QuoteElem
import com.kurban.xuehuaim.sdk.model.RedPacketDetail
import com.kurban.xuehuaim.sdk.model.RedPacketInfo
import com.kurban.xuehuaim.sdk.model.SearchParams
import com.kurban.xuehuaim.sdk.model.SendRedPacketRequest
import com.kurban.xuehuaim.sdk.model.ReportInfo
import com.kurban.xuehuaim.sdk.model.SearchResult
import com.kurban.xuehuaim.sdk.model.SoundElem
import com.kurban.xuehuaim.sdk.model.TextElem
import com.kurban.xuehuaim.sdk.model.UserFullInfo
import com.kurban.xuehuaim.sdk.model.UserInfo
import com.kurban.xuehuaim.sdk.model.UserStatusInfo
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
import com.kurban.xuehuaim.sdk.platform.FileSystem
import com.kurban.xuehuaim.sdk.platform.ioDispatcher
import com.kurban.xuehuaim.sdk.platform.sdkScope
import com.kurban.xuehuaim.sdk.sync.FavoriteSync
import com.kurban.xuehuaim.sdk.sync.FriendSync
import com.kurban.xuehuaim.sdk.sync.GroupSync
import com.kurban.xuehuaim.sdk.sync.MessageDisplayEnricher
import com.kurban.xuehuaim.sdk.sync.MomentSync
import com.kurban.xuehuaim.sdk.util.ClientMsgIdGenerator
import com.kurban.xuehuaim.sdk.util.ConversationMessageUpdater
import com.kurban.xuehuaim.sdk.util.OpenImUtils
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
    private val fileUploadService: FileUploadService,
    private val fileSystem: FileSystem,
) {
    private var conversationManager: ConversationManager? = null
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
                        throw XueHuaException.from(SdkErrorCode.MSG_SEND_FAILED, "Message is repeated")
                    MessageStatus.SEND_FAILED, MessageStatus.SENDING -> Unit
                    else -> throw XueHuaException.from(SdkErrorCode.MSG_SEND_FAILED, "Message is repeated")
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
                eventEmitter.emitConversation(com.kurban.xuehuaim.sdk.event.ConversationEvent.Changed(conv))
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
                    com.kurban.xuehuaim.sdk.event.MessageEvent.ReadReceipt(conversationId, emptyList()),
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
            delay(delayMs)
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
    ): Message = createVideoMessageFromBytes(this, bytes, fileName, duration, videoType, snapshotBytes)

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

class ConversationManager internal constructor(
    private val apiService: ImApiService,
    private val databaseService: DatabaseService,
    private val webSocketService: WebSocketService,
    private val eventEmitter: SdkEventEmitter,
    private val loginUserId: LoginUserIdProvider,
) {
    private val markingAsRead = mutableSetOf<String>()

    fun getConversationIDBySessionType(sourceID: String, sessionType: Int): String =
        OpenImUtils.getConversationIDBySessionType(
            loginUserId().orEmpty(),
            sourceID,
            sessionType,
        )

    suspend fun getOneConversation(sourceID: String, sessionType: Int): ConversationInfo =
        withContext(ioDispatcher) {
            val conversationId = getConversationIDBySessionType(sourceID, sessionType)
            databaseService.getConversation(conversationId) ?: run {
                val created = ConversationInfo(
                    conversationID = conversationId,
                    conversationType = ConversationType.entries.find { it.value == sessionType },
                    userID = if (sessionType == ConversationType.SINGLE.value) sourceID else null,
                    groupID = if (sessionType != ConversationType.SINGLE.value) sourceID else null,
                    unreadCount = 0,
                )
                databaseService.insertOrReplaceConversation(created)
                databaseService.getConversation(conversationId) ?: created
            }
        }

    suspend fun getMultipleConversation(conversationIDList: List<String>): List<ConversationInfo> =
        withContext(ioDispatcher) {
            databaseService.getMultipleConversations(conversationIDList)
        }

    suspend fun searchConversations(name: String): List<ConversationInfo> =
        withContext(ioDispatcher) {
            databaseService.searchConversations(name)
        }

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

    suspend fun markAllConversationMessageAsRead() = withContext(ioDispatcher) {
        val unread = databaseService.getAllConversations().filter { it.unreadCount > 0 }
        unread.forEach { conv ->
            runCatching { markConversationMessageAsRead(conv.conversationID) }
        }
        emitTotalUnread()
    }

    suspend fun markMessagesAsReadByMsgID(conversationId: String, clientMsgIDs: List<String>) =
        withContext(ioDispatcher) {
            if (clientMsgIDs.isEmpty()) return@withContext
            val conv = databaseService.getConversation(conversationId) ?: return@withContext
            val msgs = databaseService.getMessagesByClientMsgIds(clientMsgIDs)
            val selfUserId = loginUserId.requireUserId()
            val seqs = mutableListOf<Long>()
            val asReadIds = mutableListOf<String>()
            msgs.forEach { msg ->
                if (msg.isRead != true && msg.sendID != selfUserId) {
                    val seq = msg.seq
                    if (seq > 0) {
                        seqs.add(seq)
                        msg.clientMsgID?.let(asReadIds::add)
                    }
                }
            }
            if (seqs.isEmpty()) return@withContext
            runCatching {
                apiService.markMsgsAsRead(selfUserId, conversationId, seqs)
            }
            val decr = databaseService.markConversationMessageAsReadDB(conversationId, asReadIds)
            databaseService.decrConversationUnreadCount(conversationId, decr)
            notifyConversationChanged(conversationId)
        }

    suspend fun clearConversationAndDeleteAllMsg(conversationId: String) =
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
            databaseService.clearConversation(conversationId)
            notifyConversationChanged(conversationId)
            emitTotalUnread()
        }

    suspend fun hideAllConversations() = withContext(ioDispatcher) {
        databaseService.hideAllConversations()
        val allConvs = databaseService.getVisibleConversations()
        allConvs.forEach { conv ->
            eventEmitter.emitConversation(com.kurban.xuehuaim.sdk.event.ConversationEvent.Changed(conv))
        }
        emitTotalUnread()
    }

    suspend fun changeInputStates(conversationId: String, focus: Boolean) =
        withContext(ioDispatcher) {
            val conv = databaseService.getConversation(conversationId) ?: return@withContext
            val typingPayload = """{"msgTips":"${if (focus) "yes" else "no"}"}"""
            val msgData = buildMap {
                put("sendID", loginUserId.requireUserId())
                put("recvID", conv.userID.orEmpty())
                put("groupID", conv.groupID.orEmpty())
                put("clientMsgID", ClientMsgIdGenerator.generate())
                put("sessionType", conv.conversationType?.value ?: ConversationType.SINGLE.value)
                put("msgFrom", 100)
                put("contentType", MessageType.TYPING.value)
                put("content", typingPayload)
                put("senderPlatformID", currentPlatform().value)
                put("createTime", com.kurban.xuehuaim.sdk.util.System.currentTimeMillis())
                put("sendTime", 0)
                put(
                    "options",
                    mapOf(
                        "history" to false,
                        "persistent" to false,
                        "senderSync" to false,
                        "conversationUpdate" to false,
                        "senderConversationUpdate" to false,
                        "unreadCount" to false,
                        "offlinePush" to false,
                    ),
                )
            }
            webSocketService.sendRequest(
                WsRequest(
                    reqIdentifier = WsIdentifier.SEND_MSG,
                    data = Json.encodeToString(msgData).encodeToByteArray(),
                ),
            )
        }

    suspend fun getInputStates(conversationId: String, userID: String): List<Int> =
        withContext(ioDispatcher) {
            emptyList()
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
    private val databaseService: DatabaseService,
    private val eventEmitter: SdkEventEmitter,
    private val loginUserId: LoginUserIdProvider,
) {
    suspend fun getFriendList(filterBlack: Boolean = false): List<FriendInfo> =
        withContext(ioDispatcher) {
            val userId = loginUserId.requireUserId()
            var list = databaseService.getAllFriends()
            if (list.isEmpty()) {
                FriendSync.syncFriends(apiService, databaseService, eventEmitter, userId)
                list = databaseService.getAllFriends()
            }
            if (list.isEmpty()) {
                list = apiService.getFriendList(userId)
                databaseService.batchUpsertFriends(list)
            }
            if (filterBlack) {
                val blackIds = databaseService.getBlackUserIds()
                list.filter { it.userID !in blackIds }
            } else {
                list
            }
        }

    suspend fun getFriendListPage(
        filterBlack: Boolean = false,
        offset: Int = 0,
        count: Int = 40,
    ): List<FriendInfo> = databaseService.getFriendsPage(offset, count, filterBlack)

    suspend fun addFriend(userId: String, reqMsg: String = "") = withContext(ioDispatcher) {
        apiService.addFriendRequest(userId, reqMsg)
    }

    suspend fun deleteFriend(userId: String) = withContext(ioDispatcher) {
        apiService.deleteFriend(loginUserId.requireUserId(), userId)
        databaseService.deleteFriend(userId)
        eventEmitter.emitFriendship(
            com.kurban.xuehuaim.sdk.event.FriendshipEvent.FriendDeleted(userId),
        )
    }

    suspend fun getFriendApplications(): List<FriendApplicationInfo> =
        apiService.getFriendApplications(loginUserId.requireUserId())

    suspend fun acceptFriendApplication(toUserID: String, handleMsg: String = "") =
        respondFriendApplication(toUserID, accept = true, handleMsg)

    suspend fun refuseFriendApplication(toUserID: String, handleMsg: String = "") =
        respondFriendApplication(toUserID, accept = false, handleMsg)

    private suspend fun respondFriendApplication(
        toUserID: String,
        accept: Boolean,
        handleMsg: String,
    ) = withContext(ioDispatcher) {
        apiService.respondFriendApplication(toUserID, accept, handleMsg)
        FriendSync.syncFriends(apiService, databaseService, eventEmitter, loginUserId.requireUserId())
    }

    suspend fun getBlacklist(): List<BlacklistInfo> = withContext(ioDispatcher) {
        val userId = loginUserId.requireUserId()
        var list = databaseService.getBlackList()
        if (list.isEmpty()) {
            FriendSync.syncBlackList(apiService, databaseService, userId)
            list = databaseService.getBlackList()
        }
        if (list.isEmpty()) {
            list = apiService.getBlackList(userId)
            list.forEach { databaseService.insertOrReplaceBlack(it) }
        }
        list
    }

    suspend fun addBlacklist(userID: String, ex: String? = null) = withContext(ioDispatcher) {
        apiService.addBlack(loginUserId.requireUserId(), userID)
        FriendSync.syncBlackList(apiService, databaseService, loginUserId.requireUserId())
    }

    suspend fun removeBlacklist(userID: String) = withContext(ioDispatcher) {
        apiService.removeBlack(loginUserId.requireUserId(), userID)
        databaseService.deleteBlack(userID)
    }

    suspend fun searchUsers(keyword: String): List<UserInfo> = apiService.searchUsers(keyword)

    suspend fun getFriendsInfo(userIds: List<String>): List<FriendInfo> =
        getFriendsInfo(apiService, loginUserId, userIds)

    suspend fun checkFriend(userId: String): Boolean =
        checkFriend(apiService, loginUserId, userId)

    suspend fun searchFriends(keyword: String): List<FriendInfo> =
        searchFriends(apiService, keyword)

    suspend fun updateFriends(userIds: List<String>, remark: String? = null) =
        updateFriends(apiService, loginUserId, userIds, remark)

    suspend fun getFriendApplicationListAsRecipient(
        pageNumber: Int = 1,
        pageSize: Int = 100,
    ) = getFriendApplicationListAsRecipient(apiService, loginUserId, pageNumber, pageSize)

    suspend fun getFriendApplicationListAsApplicant(
        pageNumber: Int = 1,
        pageSize: Int = 100,
    ) = getFriendApplicationListAsApplicant(apiService, loginUserId, pageNumber, pageSize)

    suspend fun getFriendApplicationUnhandledCount(time: Long = 0): Int =
        getFriendApplicationUnhandledCount(apiService, loginUserId, time)
}

class GroupManager internal constructor(
    private val apiService: ImApiService,
    private val databaseService: DatabaseService,
    private val eventEmitter: SdkEventEmitter,
    private val loginUserId: LoginUserIdProvider,
) {
    suspend fun getJoinedGroupList(): List<GroupInfo> = withContext(ioDispatcher) {
        val userId = loginUserId.requireUserId()
        var groups = databaseService.getAllGroups()
        if (groups.isEmpty()) {
            GroupSync.syncJoinedGroups(apiService, databaseService, eventEmitter, userId)
            groups = databaseService.getAllGroups()
        }
        if (groups.isEmpty()) {
            groups = apiService.getJoinedGroupList(userId)
            databaseService.batchUpsertGroups(groups)
        }
        groups
    }

    suspend fun createGroup(groupName: String, memberUserIds: List<String>): GroupInfo =
        withContext(ioDispatcher) {
            val group = apiService.createGroup(groupName, memberUserIds)
            databaseService.insertOrReplaceGroup(group)
            eventEmitter.emitGroup(com.kurban.xuehuaim.sdk.event.GroupEvent.GroupInfoChanged(group))
            group
        }

    suspend fun dismissGroup(groupId: String) = withContext(ioDispatcher) {
        apiService.dismissGroup(groupId)
        databaseService.deleteGroupMembers(groupId)
        databaseService.deleteGroup(groupId)
        eventEmitter.emitGroup(com.kurban.xuehuaim.sdk.event.GroupEvent.GroupDismissed(groupId))
    }

    suspend fun quitGroup(groupId: String) = withContext(ioDispatcher) {
        val userId = loginUserId.requireUserId()
        apiService.quitGroup(userId, groupId)
        GroupSync.syncJoinedGroups(apiService, databaseService, eventEmitter, userId)
        eventEmitter.emitGroup(com.kurban.xuehuaim.sdk.event.GroupEvent.GroupDismissed(groupId))
    }

    suspend fun getGroupsInfo(groupIds: List<String>): List<GroupInfo> =
        apiService.getGroupsInfo(groupIds)

    suspend fun setGroupInfo(groupInfo: GroupInfo) = withContext(ioDispatcher) {
        apiService.setGroupInfoEx(groupInfo)
        databaseService.insertOrReplaceGroup(groupInfo)
        eventEmitter.emitGroup(com.kurban.xuehuaim.sdk.event.GroupEvent.GroupInfoChanged(groupInfo))
    }

    suspend fun getGroupMemberList(
        groupID: String,
        filter: Int = 0,
        offset: Int = 0,
        count: Int = 40,
    ): List<GroupMemberInfo> = withContext(ioDispatcher) {
        GroupSync.ensureGroupMembersSynced(apiService, databaseService, groupID)
        val page = databaseService.getGroupMembersPage(groupID, offset, count, filter)
        if (page.isNotEmpty()) return@withContext page
        apiService.getGroupMembers(groupID).let { members ->
            databaseService.batchUpsertGroupMembers(members)
            members.drop(offset).take(count).let { fallback ->
                if (filter <= 0) fallback else fallback.filter { it.roleLevel?.value == filter }
            }
        }
    }

    suspend fun inviteUserToGroup(
        groupID: String,
        userIDList: List<String>,
        reason: String? = null,
    ) = withContext(ioDispatcher) {
        apiService.inviteToGroup(groupID, userIDList)
        GroupSync.syncGroupInfoAndMembers(apiService, databaseService, groupID)
    }

    suspend fun kickGroupMember(
        groupID: String,
        userIDList: List<String>,
        reason: String? = null,
    ) = withContext(ioDispatcher) {
        apiService.kickGroupMember(groupID, userIDList, reason)
        GroupSync.syncGroupInfoAndMembers(apiService, databaseService, groupID)
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

    suspend fun joinGroup(
        groupId: String,
        reqMessage: String = "",
        joinSource: Int = 3,
        inviterUserID: String = "",
        ex: String = "",
    ) = withContext(ioDispatcher) {
        apiService.joinGroup(groupId, reqMessage, joinSource, inviterUserID, ex)
        GroupSync.syncJoinedGroups(
            apiService,
            databaseService,
            eventEmitter,
            loginUserId.requireUserId(),
        )
    }

    suspend fun acceptGroupApplication(
        groupId: String,
        userId: String,
        handleMsg: String = "",
    ) = withContext(ioDispatcher) {
        apiService.groupApplicationResponse(groupId, userId, handleMsg, handleResult = 1)
        GroupSync.syncGroupInfoAndMembers(apiService, databaseService, groupId)
    }

    suspend fun refuseGroupApplication(
        groupId: String,
        userId: String,
        handleMsg: String = "",
    ) = withContext(ioDispatcher) {
        apiService.groupApplicationResponse(groupId, userId, handleMsg, handleResult = -1)
    }

    suspend fun getGroupApplicationUnhandledCount(time: Long = 0): Int =
        withContext(ioDispatcher) {
            apiService.getGroupApplicationUnhandledCount(loginUserId.requireUserId(), time)
        }

    suspend fun getJoinedGroupListPage(pageNumber: Int = 1, pageSize: Int = 40): List<GroupInfo> =
        getJoinedGroupListPage(apiService, loginUserId, pageNumber, pageSize)

    suspend fun isJoinedGroup(groupId: String): Boolean =
        isJoinedGroup(apiService, loginUserId, groupId)

    suspend fun searchGroups(keyword: String): List<GroupInfo> =
        searchGroups(apiService, keyword)

    suspend fun searchGroupMembers(groupId: String, keyword: String): List<GroupMemberInfo> =
        searchGroupMembers(apiService, groupId, keyword)

    suspend fun getGroupMembersInfo(groupId: String, userIds: List<String>): List<GroupMemberInfo> =
        getGroupMembersInfo(apiService, groupId, userIds)

    suspend fun getGroupOwnerAndAdmin(groupId: String): List<GroupMemberInfo> =
        getGroupOwnerAndAdmin(apiService, groupId)

    suspend fun setGroupMemberInfo(
        groupId: String,
        userId: String,
        nickname: String? = null,
        faceURL: String? = null,
    ) = setGroupMemberInfo(apiService, groupId, userId, nickname, faceURL)

    suspend fun transferGroupOwner(groupId: String, newOwnerUserId: String) =
        transferGroupOwner(apiService, groupId, newOwnerUserId)

    suspend fun getGroupMemberListByJoinTime(
        groupID: String,
        offset: Int = 0,
        count: Int = 0,
        joinTimeBegin: Long = 0,
        joinTimeEnd: Long = 0,
        filterUserIDList: List<String> = emptyList(),
    ): List<GroupMemberInfo> = withContext(ioDispatcher) {
        GroupSync.ensureGroupMembersSynced(apiService, databaseService, groupID)
        val pageCount = if (count == 0) 40 else count
        var members = databaseService.getGroupMembersPage(groupID, offset, pageCount)
        if (joinTimeBegin > 0 || joinTimeEnd > 0) {
            members = members.filter { member ->
                val joinTime = member.joinTime ?: 0
                (joinTimeBegin <= 0 || joinTime >= joinTimeBegin) &&
                    (joinTimeEnd <= 0 || joinTime <= joinTimeEnd)
            }
        }
        if (filterUserIDList.isNotEmpty()) {
            members = members.filter { it.userID !in filterUserIDList }
        }
        members
    }

    suspend fun getUsersInGroup(groupID: String, userIDList: List<String>): List<String> =
        withContext(ioDispatcher) {
            getGroupMembersInfo(apiService, groupID, userIDList).map { it.userID }
        }

    suspend fun changeGroupMute(groupId: String, isMute: Boolean) =
        changeGroupMute(apiService, groupId, isMute)

    suspend fun changeGroupMemberMute(groupId: String, userId: String, mutedSeconds: Long) =
        changeGroupMemberMute(apiService, groupId, userId, mutedSeconds)
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

    suspend fun getUsersInfoWithCache(userIds: List<String>): List<UserInfo> =
        getUsersInfoWithCache(databaseService, userIds)

    suspend fun getUsersInfoFromSrv(userIds: List<String>): List<UserInfo> =
        getUsersInfo(userIds)

    suspend fun getSelfUserInfo(): UserInfo? =
        getSelfUserInfo(databaseService, loginUserId)

    suspend fun subscribeUsersStatus(userIds: List<String>): List<UserStatusInfo> =
        subscribeUsersStatus(apiService, loginUserId, userIds)

    suspend fun unsubscribeUsersStatus(userIds: List<String>) =
        unsubscribeUsersStatus(apiService, loginUserId, userIds)

    suspend fun getSubscribeUsersStatus(): List<UserStatusInfo> =
        getSubscribeUsersStatus(apiService, loginUserId)

    suspend fun getUserStatus(userIds: List<String>): List<UserStatusInfo> =
        getUserStatus(apiService, loginUserId, userIds)

    suspend fun getUserClientConfig(): Map<String, String> =
        getUserClientConfig(apiService, loginUserId)

    suspend fun searchFriendInfo(keyword: String): List<FriendInfo> =
        searchFriendInfo(apiService, keyword)

    suspend fun searchUserFullInfo(keyword: String): List<UserFullInfo> =
        searchUserFullInfo(apiService, keyword)

    suspend fun getRtcToken(roomId: String, userId: String): String =
        withContext(ioDispatcher) { apiService.getRtcToken(roomId, userId).token }

    suspend fun resetPaymentPassword(
        verifyCode: String,
        newPaymentPassword: String,
        areaCode: String? = null,
        phoneNumber: String? = null,
        email: String? = null,
    ) = resetPaymentPassword(
        apiService,
        verifyCode,
        newPaymentPassword,
        areaCode,
        phoneNumber,
        email,
    )

    suspend fun deleteAccount(currentPassword: String) = withContext(ioDispatcher) {
        apiService.deleteAccount(currentPassword)
    }
}

class MomentsManager internal constructor(
    private val apiService: ImApiService,
    private val databaseService: DatabaseService,
    private val eventEmitter: SdkEventEmitter,
    private val loginUserId: LoginUserIdProvider,
    private val scope: CoroutineScope = sdkScope,
) {
    suspend fun createMoment(content: String): MomentInfo {
        val moment = apiService.createMoment(content = content)
        eventEmitter.emitMoments(com.kurban.xuehuaim.sdk.event.MomentsEvent.NewMoment(moment))
        return moment
    }

    suspend fun deleteMoment(momentId: String) = withContext(ioDispatcher) {
        apiService.deleteMoment(momentId)
        databaseService.deleteMoment(momentId)
        eventEmitter.emitMoments(com.kurban.xuehuaim.sdk.event.MomentsEvent.MomentDeleted(momentId))
    }

    suspend fun getMomentList(
        ownerUserID: String? = null,
        pageNumber: Int = 1,
        showNumber: Int = 20,
    ): MomentListResponse = MomentSync.getMomentList(
        apiService = apiService,
        databaseService = databaseService,
        eventEmitter = eventEmitter,
        scope = scope,
        ownerUserID = ownerUserID,
        pageNumber = pageNumber,
        showNumber = showNumber,
    )

    suspend fun likeMoment(momentId: String) = withContext(ioDispatcher) {
        val like = apiService.likeMoment(momentId, ownerUserID = loginUserId())
        eventEmitter.emitMoments(com.kurban.xuehuaim.sdk.event.MomentsEvent.Liked(momentId, like))
    }

    suspend fun commentMoment(momentId: String, content: String) = withContext(ioDispatcher) {
        val comment = apiService.commentMoment(momentId, content, ownerUserID = loginUserId())
        eventEmitter.emitMoments(com.kurban.xuehuaim.sdk.event.MomentsEvent.Commented(momentId, comment))
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

    suspend fun fetchMomentListFromServer(
        ownerUserID: String? = null,
        pageNumber: Int = 1,
        showNumber: Int = 20,
    ): MomentListResponse = MomentSync.fetchMomentListFromServer(
        apiService = apiService,
        databaseService = databaseService,
        eventEmitter = eventEmitter,
        ownerUserID = ownerUserID,
        pageNumber = pageNumber,
        showNumber = showNumber,
    )

    suspend fun syncFromServer(pageNumber: Int = 1, showNumber: Int = 20): List<MomentInfo> =
        MomentSync.syncFromServer(
            apiService,
            databaseService,
            eventEmitter,
            pageNumber,
            showNumber,
        )

    suspend fun handleNotification(key: String, data: Map<String, String>) =
        handleNotification(eventEmitter, key, data)
}

class FavoriteManager internal constructor(
    private val apiService: ImApiService,
    private val databaseService: DatabaseService,
    private val eventEmitter: SdkEventEmitter,
) {
    suspend fun getFavoriteList(): List<FavoriteItem> = withContext(ioDispatcher) {
        val local = databaseService.getFavoritesPage(0, 1000)
        if (local.isNotEmpty()) return@withContext local
        FavoriteSync.syncFromServer(apiService, databaseService, eventEmitter)
    }

    suspend fun addFavorite(item: FavoriteItem) = withContext(ioDispatcher) {
        val saved = apiService.addFavorite(item)
        databaseService.batchUpsertFavorites(listOf(saved))
        eventEmitter.emitFavorite(com.kurban.xuehuaim.sdk.event.FavoriteEvent.Added(saved))
        saved
    }

    suspend fun deleteFavorite(favoriteId: String) = withContext(ioDispatcher) {
        apiService.deleteFavorite(favoriteId)
        databaseService.deleteFavorite(favoriteId)
        eventEmitter.emitFavorite(com.kurban.xuehuaim.sdk.event.FavoriteEvent.Deleted(favoriteId))
    }

    suspend fun fetchFavoriteListFromServer(pageNumber: Int = 1, showNumber: Int = 20) =
        fetchFavoriteListFromServer(apiService, pageNumber, showNumber)

    suspend fun syncFromServer(pageNumber: Int = 1, showNumber: Int = 100): List<FavoriteItem> =
        FavoriteSync.syncFromServer(apiService, databaseService, eventEmitter, pageNumber, showNumber)

    suspend fun isFavorited(targetType: String, targetId: String): Boolean =
        isFavorited(apiService, targetType, targetId)

    suspend fun isMessageFavorited(clientMsgId: String): Boolean =
        isMessageFavorited(apiService, clientMsgId)

    suspend fun isMomentFavorited(momentId: String): Boolean =
        isMomentFavorited(apiService, momentId)

    suspend fun addMessage(message: Message) =
        addMessage(apiService, eventEmitter, message)

    suspend fun removeMessage(clientMsgId: String) =
        removeMessage(apiService, eventEmitter, clientMsgId)

    suspend fun addMoment(moment: MomentInfo) =
        addMoment(apiService, eventEmitter, moment)

    suspend fun removeMoment(momentId: String) =
        removeMoment(apiService, eventEmitter, momentId)

    suspend fun addMomentComment(comment: MomentCommentWithUser): FavoriteItem? =
        addMomentComment(apiService, eventEmitter, comment)

    suspend fun removeMomentComment(commentID: String): Boolean =
        removeMomentComment(apiService, eventEmitter, commentID)

    suspend fun addNote(title: String, content: String) =
        addNote(apiService, eventEmitter, title, content)

    suspend fun updateNote(favoriteId: String, data: String) =
        updateNote(apiService, eventEmitter, favoriteId, data)

    suspend fun addLink(linkId: String, data: String) =
        addLink(apiService, eventEmitter, linkId, data)

    suspend fun updateFavorite(favoriteId: String, data: String) =
        updateFavorite(apiService, eventEmitter, favoriteId, data)

    suspend fun removeFavoriteItem(targetType: String, targetId: String) =
        removeFavoriteItem(apiService, eventEmitter, targetType, targetId)
}

class RedPacketManager internal constructor(
    private val apiService: ImApiService,
    private val databaseService: DatabaseService,
    private val eventEmitter: SdkEventEmitter,
) {
    private var cachedBalance: Double = 0.0
    val cachedBalanceValue: Double get() = cachedBalance
    private val grabbedPacketIds = mutableSetOf<String>()

    suspend fun getPointsBalance(): Double = withContext(ioDispatcher) {
        cachedBalance = ((apiService.getPointsBalance() * 100).toLong() / 100.0)
        cachedBalance
    }

    suspend fun getPointsTransactions(
        pageNumber: Int = 1,
        showNumber: Int = 20,
        txType: Int? = null,
    ): Pair<Int, List<PointsTransaction>> = withContext(ioDispatcher) {
        apiService.getPointsTransactions(pageNumber, showNumber, txType)
    }

    suspend fun getIncomeTransactions(
        pageNumber: Int = 1,
        showNumber: Int = 20,
    ): Pair<Int, List<PointsTransaction>> = withContext(ioDispatcher) {
        val (total, items) = getPointsTransactions(pageNumber, showNumber)
        total to items.filter { it.isIncome }
    }

    suspend fun getExpenseTransactions(
        pageNumber: Int = 1,
        showNumber: Int = 20,
    ): Pair<Int, List<PointsTransaction>> = withContext(ioDispatcher) {
        val (total, items) = getPointsTransactions(pageNumber, showNumber)
        total to items.filter { it.isExpense }
    }

    suspend fun sendRedPacket(req: SendRedPacketRequest): String = withContext(ioDispatcher) {
        val packetId = apiService.sendRedPacket(req)
        cachedBalance = ((cachedBalance - req.totalAmount) * 100).toLong() / 100.0
        packetId
    }

    suspend fun grabRedPacket(packetId: String): Double = withContext(ioDispatcher) {
        val amount = apiService.grabRedPacket(packetId)
        cachedBalance = ((cachedBalance + amount) * 100).toLong() / 100.0
        markGrabbed(packetId)
        eventEmitter.emitRedPacket(
            com.kurban.xuehuaim.sdk.event.RedPacketEvent.Grabbed(packetId, amount),
        )
        amount
    }

    suspend fun getRedPacketDetail(packetId: String): RedPacketDetail =
        withContext(ioDispatcher) {
            apiService.getRedPacketDetail(packetId)
        }

    fun isGrabbed(packetId: String): Boolean = grabbedPacketIds.contains(packetId)

    suspend fun preloadGrabbedStatus(packetIds: List<String>) = withContext(ioDispatcher) {
        if (packetIds.isEmpty()) return@withContext
        val uncached = packetIds.filter { !grabbedPacketIds.contains(it) }
        if (uncached.isEmpty()) return@withContext
        val grabbed = databaseService.selectGrabbedRedPacketIds(uncached)
        grabbedPacketIds.addAll(grabbed)
    }

    suspend fun markGrabbed(packetId: String) = withContext(ioDispatcher) {
        grabbedPacketIds.add(packetId)
        databaseService.markRedPacketGrabbed(packetId)
    }
}

class ReportAppealManager internal constructor(
    private val apiService: ImApiService,
) {
    suspend fun submitReport(report: ReportInfo): ReportInfo = apiService.submitReport(report)
    suspend fun submitAppeal(appeal: AppealInfo): AppealInfo = apiService.submitAppeal(appeal)

    suspend fun reportUser(
        targetUserId: String,
        category: String,
        description: String? = null,
        evidenceUrls: List<String>? = null,
    ) = reportUser(apiService, targetUserId, category, description, evidenceUrls)

    suspend fun reportGroup(
        targetGroupId: String,
        category: String,
        description: String? = null,
        evidenceUrls: List<String>? = null,
    ) = reportGroup(apiService, targetGroupId, category, description, evidenceUrls)

    suspend fun reportMessage(
        targetUserId: String,
        messageId: String,
        category: String,
        description: String? = null,
        evidenceUrls: List<String>? = null,
    ) = reportMessage(apiService, targetUserId, messageId, category, description, evidenceUrls)

    suspend fun getAppealCaptcha() = getAppealCaptcha(apiService)

    suspend fun uploadAppealEvidence(appealToken: String, bytes: ByteArray, fileName: String) =
        uploadAppealEvidence(apiService, appealToken, bytes, fileName)
}

class ApplicationManager internal constructor(
    private val apiService: ImApiService,
) {
    suspend fun checkVersion(): ApplicationVersionInfo = apiService.checkAppVersion()

    suspend fun getLatestVersion(
        platform: String,
        currentVersion: String? = null,
    ) = getLatestVersion(apiService, platform, currentVersion)

    suspend fun getLatestVersion(currentVersion: String? = null) =
        getLatestVersion(apiService, currentVersion)
}
