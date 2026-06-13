package com.kurban.xuehuaim.sdk.manager

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.enum.ConversationType
import com.kurban.xuehuaim.sdk.enum.MessageStatus
import com.kurban.xuehuaim.sdk.enum.MessageType
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.model.AdvancedMessage
import com.kurban.xuehuaim.sdk.model.AdvancedTextElem
import com.kurban.xuehuaim.sdk.model.CallSignalElem
import com.kurban.xuehuaim.sdk.model.FileElem
import com.kurban.xuehuaim.sdk.model.Message
import com.kurban.xuehuaim.sdk.model.MessageEntity
import com.kurban.xuehuaim.sdk.model.PictureElem
import com.kurban.xuehuaim.sdk.model.PictureInfo
import com.kurban.xuehuaim.sdk.model.QuoteElem
import com.kurban.xuehuaim.sdk.model.RedPacketElem
import com.kurban.xuehuaim.sdk.model.SoundElem
import com.kurban.xuehuaim.sdk.model.VideoElem
import com.kurban.xuehuaim.sdk.network.http.LoginUserIdProvider
import com.kurban.xuehuaim.sdk.platform.FileSystem
import com.kurban.xuehuaim.sdk.platform.ioDispatcher
import com.kurban.xuehuaim.sdk.util.ClientMsgIdGenerator
import com.kurban.xuehuaim.sdk.util.ConversationMessageUpdater
import com.kurban.xuehuaim.sdk.util.OpenImUtils
import com.kurban.xuehuaim.sdk.util.withParsedContent
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

suspend fun MessageManager.createCallSignalMessage(elem: CallSignalElem): Message =
    createMessage(MessageType.CALL_SIGNAL, Json.encodeToString(elem))

suspend fun MessageManager.createRedPacketMessage(elem: RedPacketElem): Message =
    createMessage(MessageType.RED_PACKET, Json.encodeToString(elem), redPacketElem = elem)

suspend fun MessageManager.createAdvancedTextMessage(
    text: String,
    entities: List<MessageEntity> = emptyList(),
): Message {
    val elem = AdvancedTextElem(text = text, messageEntityList = entities)
    return createMessage(MessageType.ADVANCED_TEXT, Json.encodeToString(elem))
}

suspend fun MessageManager.createAdvancedQuoteMessage(
    text: String,
    quoteMessage: Message,
): Message = createQuoteMessage(quoteMessage, text)

suspend fun MessageManager.createImageMessageByURL(
    imageUrl: String,
    width: Int = 0,
    height: Int = 0,
): Message = createImageMessage(imageUrl, width, height)

suspend fun MessageManager.createSoundMessageByURL(sourceUrl: String, duration: Long): Message =
    createSoundMessage(sourceUrl, duration)

suspend fun MessageManager.createVideoMessageByURL(
    videoUrl: String,
    duration: Long,
    snapshotUrl: String? = null,
): Message = createVideoMessage(videoUrl, duration, snapshotUrl)

suspend fun MessageManager.createFileMessageByURL(
    sourceUrl: String,
    fileName: String,
    fileSize: Long = 0,
): Message = createFileMessage(sourceUrl, fileName, fileSize)

internal suspend fun MessageManager.createImageMessageFromFullPath(
    fileSystem: FileSystem,
    path: String,
): Message {
    val fileSize = fileSystem.fileSize(path)
    val elem = PictureElem(
        sourcePath = path,
        sourcePicture = PictureInfo(size = fileSize),
    )
    return Message(
        clientMsgID = ClientMsgIdGenerator.generate(),
        contentType = MessageType.PICTURE,
        content = Json.encodeToString(elem),
        pictureElem = elem,
        createTime = com.kurban.xuehuaim.sdk.util.System.currentTimeMillis(),
    ).withParsedContent()
}

internal suspend fun MessageManager.createImageMessageFromBytes(
    manager: MessageManager,
    bytes: ByteArray,
    fileName: String,
): Message {
    val message = Message(
        clientMsgID = ClientMsgIdGenerator.generate(),
        contentType = MessageType.PICTURE,
        pictureElem = PictureElem(
            sourcePath = fileName,
            sourcePicture = PictureInfo(size = bytes.size.toLong()),
        ),
        createTime = com.kurban.xuehuaim.sdk.util.System.currentTimeMillis(),
    ).withParsedContent()
    manager.pendingUploadBytes[message.clientMsgID] = bytes
    return message.copy(
        content = Json.encodeToString(message.pictureElem),
    )
}

internal suspend fun MessageManager.createVideoMessageFromBytes(
    manager: MessageManager,
    bytes: ByteArray,
    fileName: String,
    duration: Int,
    videoType: String? = null,
    snapshotBytes: ByteArray? = null,
): Message {
    val message = Message(
        clientMsgID = ClientMsgIdGenerator.generate(),
        contentType = MessageType.VIDEO,
        videoElem = VideoElem(
            videoPath = fileName,
            videoType = videoType,
            videoSize = bytes.size.toLong(),
            duration = duration.toLong(),
        ),
        createTime = com.kurban.xuehuaim.sdk.util.System.currentTimeMillis(),
    ).withParsedContent()
    manager.pendingUploadBytes[message.clientMsgID] = bytes
    snapshotBytes?.let { manager.pendingSnapshotBytes[message.clientMsgID] = it }
    return message.copy(content = Json.encodeToString(message.videoElem))
}

internal suspend fun MessageManager.createFileMessageFromBytes(
    manager: MessageManager,
    bytes: ByteArray,
    fileName: String,
): Message {
    val message = Message(
        clientMsgID = ClientMsgIdGenerator.generate(),
        contentType = MessageType.FILE,
        fileElem = FileElem(
            filePath = fileName,
            fileName = fileName,
            fileSize = bytes.size.toLong(),
        ),
        createTime = com.kurban.xuehuaim.sdk.util.System.currentTimeMillis(),
    ).withParsedContent()
    manager.pendingUploadBytes[message.clientMsgID] = bytes
    return message.copy(content = Json.encodeToString(message.fileElem))
}

internal suspend fun MessageManager.createSoundMessageFromFullPath(
    fileSystem: FileSystem,
    path: String,
    duration: Long,
): Message {
    val elem = SoundElem(
        soundPath = path,
        duration = duration,
        dataSize = fileSystem.fileSize(path),
    )
    return Message(
        clientMsgID = ClientMsgIdGenerator.generate(),
        contentType = MessageType.VOICE,
        content = Json.encodeToString(elem),
        soundElem = elem,
        createTime = com.kurban.xuehuaim.sdk.util.System.currentTimeMillis(),
    ).withParsedContent()
}

internal suspend fun MessageManager.createVideoMessageFromFullPath(
    fileSystem: FileSystem,
    path: String,
    duration: Long,
    snapshotPath: String? = null,
): Message {
    val elem = VideoElem(
        videoPath = path,
        videoSize = fileSystem.fileSize(path),
        duration = duration,
        snapshotPath = snapshotPath,
        snapshotSize = snapshotPath?.let { fileSystem.fileSize(it) },
    )
    return Message(
        clientMsgID = ClientMsgIdGenerator.generate(),
        contentType = MessageType.VIDEO,
        content = Json.encodeToString(elem),
        videoElem = elem,
        createTime = com.kurban.xuehuaim.sdk.util.System.currentTimeMillis(),
    ).withParsedContent()
}

internal suspend fun MessageManager.createFileMessageFromFullPath(
    fileSystem: FileSystem,
    path: String,
): Message {
    val fileName = path.substringAfterLast('/')
    val elem = FileElem(
        filePath = path,
        fileName = fileName,
        fileSize = fileSystem.fileSize(path),
    )
    return Message(
        clientMsgID = ClientMsgIdGenerator.generate(),
        contentType = MessageType.FILE,
        content = Json.encodeToString(elem),
        fileElem = elem,
        createTime = com.kurban.xuehuaim.sdk.util.System.currentTimeMillis(),
    ).withParsedContent()
}

internal suspend fun MessageManager.handleMediaUploadIfNeeded(
    fileUploadService: FileUploadService,
    fileSystem: FileSystem,
    pendingUploadBytes: MutableMap<String, ByteArray>,
    pendingSnapshotBytes: MutableMap<String, ByteArray>,
    message: Message,
): Message {
    val clientMsgId = message.clientMsgID
    val pendingBytes = pendingUploadBytes.remove(clientMsgId)
    val pendingSnapshot = pendingSnapshotBytes.remove(clientMsgId)

    return when (message.contentType) {
        MessageType.PICTURE -> handlePictureUpload(
            fileUploadService, fileSystem, message, clientMsgId, pendingBytes,
        )

        MessageType.VOICE -> handleSoundUpload(
            fileUploadService, fileSystem, message, clientMsgId, pendingBytes,
        )

        MessageType.VIDEO -> handleVideoUpload(
            fileUploadService, fileSystem, message, clientMsgId, pendingBytes, pendingSnapshot,
        )

        MessageType.FILE -> handleFileUpload(
            fileUploadService, fileSystem, message, clientMsgId, pendingBytes,
        )

        else -> message
    }
}

private suspend fun handlePictureUpload(
    fileUploadService: FileUploadService,
    fileSystem: FileSystem,
    message: Message,
    clientMsgId: String,
    pendingBytes: ByteArray?,
): Message {
    val elem = message.pictureElem ?: return message
    if (!elem.sourcePicture?.url.isNullOrBlank()) return message
    val ext = filepathExt(elem.sourcePicture?.uuid, elem.sourcePath)
    val name = uploadFileName("picture", clientMsgId) + ext
    val result = when {
        pendingBytes != null -> fileUploadService.uploadFileBytes(
            pendingBytes, name, clientMsgId = clientMsgId, cause = "msg-picture",
        )

        !elem.sourcePath.isNullOrBlank() && fileSystem.exists(elem.sourcePath) ->
            fileUploadService.uploadFile(
                elem.sourcePath, name, clientMsgId = clientMsgId, cause = "msg-picture",
            )

        else -> return message
    }
    val fileSize = pendingBytes?.size?.toLong() ?: fileSystem.fileSize(elem.sourcePath.orEmpty())
    val sourcePic = (elem.sourcePicture ?: PictureInfo()).copy(
        url = result.url,
        size = fileSize,
        type = elem.sourcePicture?.type ?: guessContentType(ext),
    )
    val snapshotPic = buildSnapshotPicture(result.url, sourcePic)
    val updatedElem = elem.copy(
        sourcePicture = sourcePic,
        bigPicture = sourcePic,
        snapshotPicture = snapshotPic,
    )
    return message.copy(
        pictureElem = updatedElem,
        content = Json.encodeToString(updatedElem),
    ).withParsedContent()
}

private suspend fun handleSoundUpload(
    fileUploadService: FileUploadService,
    fileSystem: FileSystem,
    message: Message,
    clientMsgId: String,
    pendingBytes: ByteArray?,
): Message {
    val elem = message.soundElem ?: return message
    if (!elem.sourceUrl.isNullOrBlank()) return message
    val soundPath = elem.soundPath ?: return message
    val ext = filepathExt(elem.uuid, soundPath)
    val name = uploadFileName("voice", clientMsgId) + ext
    val result = if (pendingBytes != null) {
        fileUploadService.uploadFileBytes(
            pendingBytes,
            name,
            clientMsgId = clientMsgId,
            cause = "msg-voice"
        )
    } else if (fileSystem.exists(soundPath)) {
        fileUploadService.uploadFile(
            soundPath,
            name,
            clientMsgId = clientMsgId,
            cause = "msg-voice"
        )
    } else {
        return message
    }
    val fileSize = pendingBytes?.size?.toLong() ?: fileSystem.fileSize(soundPath)
    val updatedElem = elem.copy(sourceUrl = result.url, dataSize = fileSize)
    return message.copy(
        soundElem = updatedElem,
        content = Json.encodeToString(updatedElem),
    ).withParsedContent()
}

private suspend fun handleVideoUpload(
    fileUploadService: FileUploadService,
    fileSystem: FileSystem,
    message: Message,
    clientMsgId: String,
    pendingBytes: ByteArray?,
    pendingSnapshot: ByteArray?,
): Message = coroutineScope {
    val elem = message.videoElem ?: return@coroutineScope message
    var updatedElem = elem

    val snapshotDeferred = if (updatedElem.snapshotUrl.isNullOrBlank()) {
        async {
            runCatching {
                val snapExt = filepathExt(updatedElem.snapshotUUID, updatedElem.snapshotPath)
                val snapName = uploadFileName("videoSnapshot", clientMsgId) + snapExt
                val snapResult = when {
                    pendingSnapshot != null -> fileUploadService.uploadFileBytes(
                        pendingSnapshot,
                        snapName,
                        clientMsgId = "${clientMsgId}_snapshot",
                        cause = "msg-video-snapshot",
                    )

                    !updatedElem.snapshotPath.isNullOrBlank() &&
                            fileSystem.exists(updatedElem.snapshotPath!!) -> {
                        val snapshotPath = updatedElem.snapshotPath!!
                        fileUploadService.uploadFile(
                            snapshotPath,
                            snapName,
                            clientMsgId = "${clientMsgId}_snapshot",
                            cause = "msg-video-snapshot",
                        )
                    }

                    else -> return@runCatching updatedElem
                }
                val snapSize = pendingSnapshot?.size?.toLong()
                    ?: updatedElem.snapshotPath?.let { fileSystem.fileSize(it) }
                updatedElem.copy(
                    snapshotUrl = snapResult.url,
                    snapshotSize = snapSize,
                )
            }.getOrDefault(updatedElem)
        }
    } else {
        null
    }

    if (updatedElem.videoUrl.isNullOrBlank()) {
        val videoExt = filepathExt(updatedElem.videoUUID, updatedElem.videoPath)
        val videoName = uploadFileName("video", clientMsgId) + videoExt
        val videoResult = when {
            pendingBytes != null -> fileUploadService.uploadFileBytes(
                pendingBytes,
                videoName,
                clientMsgId = clientMsgId,
                cause = "msg-video",
            )

            !updatedElem.videoPath.isNullOrBlank() && fileSystem.exists(updatedElem.videoPath) ->
                fileUploadService.uploadFile(
                    updatedElem.videoPath,
                    videoName,
                    clientMsgId = clientMsgId,
                    cause = "msg-video",
                )

            else -> return@coroutineScope message
        }
        val videoSize = pendingBytes?.size?.toLong()
            ?: fileSystem.fileSize(updatedElem.videoPath.orEmpty())
        updatedElem = updatedElem.copy(
            videoUrl = videoResult.url,
            videoSize = videoSize,
            videoType = updatedElem.videoType ?: guessContentType(videoExt),
        )
    }

    snapshotDeferred?.let { updatedElem = it.await() }

    message.copy(
        videoElem = updatedElem,
        content = Json.encodeToString(updatedElem),
    ).withParsedContent()
}

private suspend fun handleFileUpload(
    fileUploadService: FileUploadService,
    fileSystem: FileSystem,
    message: Message,
    clientMsgId: String,
    pendingBytes: ByteArray?,
): Message {
    val elem = message.fileElem ?: return message
    if (!elem.sourceUrl.isNullOrBlank()) return message
    val baseName =
        elem.fileName ?: elem.filePath?.substringAfterLast('/').orEmpty().ifBlank { "file" }
    val name = "${uploadFileName("file", clientMsgId)}/$baseName"
    val result = when {
        pendingBytes != null -> fileUploadService.uploadFileBytes(
            pendingBytes, name, clientMsgId = clientMsgId, cause = "msg-file",
        )

        !elem.filePath.isNullOrBlank() && fileSystem.exists(elem.filePath) ->
            fileUploadService.uploadFile(
                elem.filePath,
                name,
                clientMsgId = clientMsgId,
                cause = "msg-file"
            )

        else -> return message
    }
    val fileSize = pendingBytes?.size?.toLong() ?: fileSystem.fileSize(elem.filePath.orEmpty())
    val updatedElem = elem.copy(sourceUrl = result.url, fileSize = fileSize)
    return message.copy(
        fileElem = updatedElem,
        content = Json.encodeToString(updatedElem),
    ).withParsedContent()
}

private fun buildSnapshotPicture(url: String, sourcePic: PictureInfo): PictureInfo {
    return runCatching {
        val separator = if (url.contains('?')) "&" else "?"
        val snapshotUrl = "$url${separator}type=image&width=640&height=640"
        PictureInfo(width = 640, height = 640, url = snapshotUrl)
    }.getOrDefault(sourcePic)
}

private fun uploadFileName(type: String, clientMsgId: String): String = "msg_${type}_$clientMsgId"

private fun filepathExt(vararg paths: String?): String {
    for (path in paths) {
        if (!path.isNullOrBlank()) {
            val dotIndex = path.lastIndexOf('.')
            if (dotIndex >= 0 && dotIndex < path.length - 1) {
                return path.substring(dotIndex)
            }
        }
    }
    return ""
}

private fun guessContentType(extOrFileName: String): String {
    val ext = extOrFileName.removePrefix(".").lowercase()
    return when (ext) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "mp4" -> "video/mp4"
        "mov" -> "video/quicktime"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        else -> "application/octet-stream"
    }
}

suspend fun MessageManager.sendMessageNotOss(
    message: Message,
    recvId: String,
    groupId: String = "",
    conversationId: String? = null,
    offlinePush: Boolean = true,
): Message = sendMessage(message, recvId, groupId, conversationId, offlinePush)

suspend fun MessageManager.getAdvancedHistoryMessageListReverse(
    conversationId: String,
    count: Int = 20,
    startClientMsgId: String? = null,
): AdvancedMessage =
    getAdvancedHistoryMessageListInternal(conversationId, count, startClientMsgId, isReverse = true)

internal suspend fun MessageManager.insertSingleMessageToLocalStorage(
    databaseService: DatabaseService,
    eventEmitter: SdkEventEmitter,
    loginUserId: LoginUserIdProvider,
    receiverId: String,
    senderId: String,
    message: Message? = null,
): Message = withContext(ioDispatcher) {
    val selfId = loginUserId() ?: senderId
    val msg = (message ?: createTextMessage("")).copy(
        sendID = senderId,
        recvID = receiverId,
        sessionType = ConversationType.SINGLE,
        status = MessageStatus.SEND_SUCCESS,
        sendTime = com.kurban.xuehuaim.sdk.util.System.currentTimeMillis(),
    ).withParsedContent()
    val conversationId = OpenImUtils.genSingleConversationID(
        if (senderId == selfId) selfId else senderId,
        if (senderId == selfId) receiverId else senderId,
    )
    val stored = msg.copy(conversationID = conversationId)
    databaseService.insertOrReplaceMessage(stored)
    ConversationMessageUpdater.updateFromMessage(
        databaseService = databaseService,
        eventEmitter = eventEmitter,
        message = stored,
        selfUserId = selfId,
        isOutgoingSend = senderId == selfId,
    )
    stored
}

internal suspend fun MessageManager.insertGroupMessageToLocalStorage(
    databaseService: DatabaseService,
    eventEmitter: SdkEventEmitter,
    loginUserId: LoginUserIdProvider,
    groupId: String,
    senderId: String,
    message: Message? = null,
): Message = withContext(ioDispatcher) {
    val selfId = loginUserId() ?: senderId
    val msg = (message ?: createTextMessage("")).copy(
        sendID = senderId,
        groupID = groupId,
        sessionType = ConversationType.SUPER_GROUP,
        status = MessageStatus.SEND_SUCCESS,
        sendTime = com.kurban.xuehuaim.sdk.util.System.currentTimeMillis(),
    ).withParsedContent()
    val conversationId = OpenImUtils.genGroupConversationID(groupId)
    val stored = msg.copy(conversationID = conversationId)
    databaseService.insertOrReplaceMessage(stored)
    ConversationMessageUpdater.updateFromMessage(
        databaseService = databaseService,
        eventEmitter = eventEmitter,
        message = stored,
        selfUserId = selfId,
        isOutgoingSend = senderId == selfId,
    )
    stored
}

internal suspend fun MessageManager.recoverSendingMessages(
    databaseService: DatabaseService,
    eventEmitter: SdkEventEmitter,
    loginUserId: LoginUserIdProvider,
) = withContext(ioDispatcher) {
    val sending = databaseService.selectSendingMessages()
    if (sending.isEmpty()) return@withContext
    val clientIds = sending.map { it.clientMsgID }
    val messages = databaseService.getMessagesByClientMsgIds(clientIds)
    val messageMap = messages.associateBy { it.clientMsgID }
    for (record in sending) {
        val clientMsgId = record.clientMsgID
        val message = messageMap[clientMsgId]
        if (message == null) continue
        val status = message.status
        if (status == MessageStatus.SEND_SUCCESS || status == MessageStatus.SEND_FAILED) continue
        if (status == MessageStatus.SENDING) {
            val failed = message.copy(status = MessageStatus.SEND_FAILED)
            databaseService.insertOrReplaceMessage(failed)
            databaseService.deleteSendingMessage(clientMsgId)
            record.conversationID.takeIf { it.isNotBlank() }?.let { convId ->
                ConversationLatestMsgHelper.updateConversationIfLatestMsg(
                    databaseService = databaseService,
                    eventEmitter = eventEmitter,
                    conversationId = convId,
                    clientMsgId = clientMsgId,
                )
            }
            eventEmitter.emitMessage(
                com.kurban.xuehuaim.sdk.event.MessageEvent.SendFailed(
                    clientMsgId = clientMsgId,
                    code = -1,
                    error = "recovered as failed",
                ),
            )
        }
    }
}

private suspend fun MessageManager.createMessage(
    type: MessageType,
    content: String,
    quoteElem: QuoteElem? = null,
    redPacketElem: RedPacketElem? = null,
): Message = Message(
    clientMsgID = ClientMsgIdGenerator.generate(),
    contentType = type,
    content = content,
    quoteElem = quoteElem,
    redPacketElem = redPacketElem,
    createTime = com.kurban.xuehuaim.sdk.util.System.currentTimeMillis(),
).withParsedContent()
