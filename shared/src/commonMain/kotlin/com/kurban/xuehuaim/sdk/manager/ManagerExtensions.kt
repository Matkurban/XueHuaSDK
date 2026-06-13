package com.kurban.xuehuaim.sdk.manager

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.enum.ConversationType
import com.kurban.xuehuaim.sdk.enum.FavoriteType
import com.kurban.xuehuaim.sdk.enum.GroupRoleLevel
import com.kurban.xuehuaim.sdk.enum.MessageStatus
import com.kurban.xuehuaim.sdk.enum.MessageType
import com.kurban.xuehuaim.sdk.event.FavoriteEvent
import com.kurban.xuehuaim.sdk.event.MomentsEvent
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.model.AdvancedTextElem
import com.kurban.xuehuaim.sdk.model.AppealCaptcha
import com.kurban.xuehuaim.sdk.model.AppealUploadResult
import com.kurban.xuehuaim.sdk.model.ApplicationVersionInfo
import com.kurban.xuehuaim.sdk.model.CallSignalElem
import com.kurban.xuehuaim.sdk.model.CreateReportResult
import com.kurban.xuehuaim.sdk.model.FavoriteItem
import com.kurban.xuehuaim.sdk.model.FavoriteListResponse
import com.kurban.xuehuaim.sdk.model.FileElem
import com.kurban.xuehuaim.sdk.model.FriendInfo
import com.kurban.xuehuaim.sdk.model.GroupInfo
import com.kurban.xuehuaim.sdk.model.GroupMemberInfo
import com.kurban.xuehuaim.sdk.model.Message
import com.kurban.xuehuaim.sdk.model.MessageEntity
import com.kurban.xuehuaim.sdk.model.MomentComment
import com.kurban.xuehuaim.sdk.model.MomentCommentWithUser
import com.kurban.xuehuaim.sdk.model.MomentInfo
import com.kurban.xuehuaim.sdk.model.MomentLike
import com.kurban.xuehuaim.sdk.model.PictureElem
import com.kurban.xuehuaim.sdk.model.PictureInfo
import com.kurban.xuehuaim.sdk.model.QuoteElem
import com.kurban.xuehuaim.sdk.model.RedPacketElem
import com.kurban.xuehuaim.sdk.model.SoundElem
import com.kurban.xuehuaim.sdk.model.UserFullInfo
import com.kurban.xuehuaim.sdk.model.UserInfo
import com.kurban.xuehuaim.sdk.model.UserStatusInfo
import com.kurban.xuehuaim.sdk.model.VideoElem
import com.kurban.xuehuaim.sdk.network.http.ImApiService
import com.kurban.xuehuaim.sdk.network.http.LoginUserIdProvider
import com.kurban.xuehuaim.sdk.network.http.applicationPlatformName
import com.kurban.xuehuaim.sdk.platform.FileSystem
import com.kurban.xuehuaim.sdk.platform.ioDispatcher
import com.kurban.xuehuaim.sdk.util.ClientMsgIdGenerator
import com.kurban.xuehuaim.sdk.util.ConversationMessageUpdater
import com.kurban.xuehuaim.sdk.util.OpenImUtils
import com.kurban.xuehuaim.sdk.util.withParsedContent
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

// ─── MessageManager extensions ───────────────────────────────────────────────

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
    fileUploadService: FileUploadService,
    path: String,
    onProgress: ((Int) -> Unit)? = null,
): Message {
    val bytes = fileSystem.readBytes(path)
    val fileName = path.substringAfterLast('/')
    val result = fileUploadService.uploadFileBytes(bytes, fileName, onProgress)
    return createImageMessage(result.url)
}

internal suspend fun MessageManager.createImageMessageFromBytes(
    fileUploadService: FileUploadService,
    bytes: ByteArray,
    fileName: String,
    onProgress: ((Int) -> Unit)? = null,
): Message {
    val result = fileUploadService.uploadFileBytes(bytes, fileName, onProgress)
    return createImageMessage(result.url)
}

internal suspend fun MessageManager.createVideoMessageFromBytes(
    fileUploadService: FileUploadService,
    bytes: ByteArray,
    fileName: String,
    duration: Int,
    videoType: String? = null,
    snapshotBytes: ByteArray? = null,
    onProgress: ((Int) -> Unit)? = null,
): Message {
    val result = fileUploadService.uploadFileBytes(bytes, fileName, onProgress)
    val snapshotUrl = snapshotBytes?.let { snapshot ->
        fileUploadService.uploadFileBytes(
            snapshot,
            "snapshot_${fileName.substringBeforeLast('.')}.jpg",
        ).url
    }
    return createVideoMessage(result.url, duration.toLong(), snapshotUrl)
}

internal suspend fun MessageManager.createFileMessageFromBytes(
    fileUploadService: FileUploadService,
    bytes: ByteArray,
    fileName: String,
    onProgress: ((Int) -> Unit)? = null,
): Message {
    val result = fileUploadService.uploadFileBytes(bytes, fileName, onProgress)
    return createFileMessage(result.url, fileName, bytes.size.toLong())
}

internal suspend fun MessageManager.createSoundMessageFromFullPath(
    fileSystem: FileSystem,
    fileUploadService: FileUploadService,
    path: String,
    duration: Long,
    onProgress: ((Int) -> Unit)? = null,
): Message {
    val bytes = fileSystem.readBytes(path)
    val fileName = path.substringAfterLast('/')
    val result = fileUploadService.uploadFileBytes(bytes, fileName, onProgress)
    return createSoundMessage(result.url, duration)
}

internal suspend fun MessageManager.createVideoMessageFromFullPath(
    fileSystem: FileSystem,
    fileUploadService: FileUploadService,
    path: String,
    duration: Long,
    snapshotPath: String? = null,
    onProgress: ((Int) -> Unit)? = null,
): Message {
    val bytes = fileSystem.readBytes(path)
    val fileName = path.substringAfterLast('/')
    val result = fileUploadService.uploadFileBytes(bytes, fileName, onProgress)
    val snapshotUrl = snapshotPath?.let { snapPath ->
        fileUploadService.uploadFileBytes(
            fileSystem.readBytes(snapPath),
            snapPath.substringAfterLast('/'),
        ).url
    }
    return createVideoMessage(result.url, duration, snapshotUrl)
}

internal suspend fun MessageManager.createFileMessageFromFullPath(
    fileSystem: FileSystem,
    fileUploadService: FileUploadService,
    path: String,
    onProgress: ((Int) -> Unit)? = null,
): Message {
    val bytes = fileSystem.readBytes(path)
    val fileName = path.substringAfterLast('/')
    val result = fileUploadService.uploadFileBytes(bytes, fileName, onProgress)
    return createFileMessage(result.url, fileName, bytes.size.toLong())
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
): List<Message> =
    getAdvancedHistoryMessageList(conversationId, count, startClientMsgId).reversed()

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

// ─── FriendshipManager extensions ────────────────────────────────────────────

internal suspend fun FriendshipManager.getFriendsInfo(
    apiService: ImApiService,
    loginUserId: LoginUserIdProvider,
    userIds: List<String>,
): List<FriendInfo> = withContext(ioDispatcher) {
    if (userIds.isEmpty()) return@withContext emptyList()
    apiService.getDesignatedFriends(loginUserId.requireUserId(), userIds)
}

internal suspend fun FriendshipManager.checkFriend(
    apiService: ImApiService,
    loginUserId: LoginUserIdProvider,
    userId: String,
): Boolean = withContext(ioDispatcher) {
    apiService.getDesignatedFriends(loginUserId.requireUserId(), listOf(userId)).isNotEmpty()
}

internal suspend fun FriendshipManager.searchFriends(apiService: ImApiService, keyword: String): List<FriendInfo> =
    withContext(ioDispatcher) { apiService.searchFriendInfo(keyword) }

internal suspend fun FriendshipManager.updateFriends(
    apiService: ImApiService,
    loginUserId: LoginUserIdProvider,
    userIds: List<String>,
    remark: String? = null,
) = withContext(ioDispatcher) {
    apiService.updateFriends(loginUserId.requireUserId(), userIds, remark)
}

internal suspend fun FriendshipManager.getFriendApplicationListAsRecipient(
    apiService: ImApiService,
    loginUserId: LoginUserIdProvider,
    pageNumber: Int = 1,
    pageSize: Int = 100,
) = withContext(ioDispatcher) {
    apiService.getRecvFriendApplications(loginUserId.requireUserId(), pageNumber, pageSize)
}

internal suspend fun FriendshipManager.getFriendApplicationListAsApplicant(
    apiService: ImApiService,
    loginUserId: LoginUserIdProvider,
    pageNumber: Int = 1,
    pageSize: Int = 100,
) = withContext(ioDispatcher) {
    apiService.getSelfFriendApplications(loginUserId.requireUserId(), pageNumber, pageSize)
}

internal suspend fun FriendshipManager.getFriendApplicationUnhandledCount(
    apiService: ImApiService,
    loginUserId: LoginUserIdProvider,
    time: Long = 0,
): Int = withContext(ioDispatcher) {
    apiService.getSelfUnhandledApplyCount(loginUserId.requireUserId(), time)
}

// ─── GroupManager extensions ─────────────────────────────────────────────────

internal suspend fun GroupManager.getJoinedGroupListPage(
    apiService: ImApiService,
    loginUserId: LoginUserIdProvider,
    pageNumber: Int,
    pageSize: Int,
) = withContext(ioDispatcher) {
    apiService.getJoinedGroupListPage(loginUserId.requireUserId(), pageNumber, pageSize)
}

internal suspend fun GroupManager.isJoinedGroup(
    apiService: ImApiService,
    loginUserId: LoginUserIdProvider,
    groupId: String,
): Boolean = withContext(ioDispatcher) {
    apiService.getJoinedGroupList(loginUserId.requireUserId())
        .any { it.groupID == groupId }
}

internal suspend fun GroupManager.searchGroups(
    apiService: ImApiService,
    keyword: String,
): List<GroupInfo> = withContext(ioDispatcher) {
    apiService.getGroupsInfo(listOf(keyword)).ifEmpty {
        apiService.getJoinedGroupList("").filter {
            it.groupName?.contains(keyword, ignoreCase = true) == true
        }
    }
}

internal suspend fun GroupManager.searchGroupMembers(
    apiService: ImApiService,
    groupId: String,
    keyword: String,
): List<GroupMemberInfo> = withContext(ioDispatcher) {
    apiService.getGroupMembers(groupId).filter {
        it.nickname?.contains(keyword, ignoreCase = true) == true ||
            it.userID.contains(keyword, ignoreCase = true)
    }
}

internal suspend fun GroupManager.getGroupMembersInfo(
    apiService: ImApiService,
    groupId: String,
    userIds: List<String>,
) = withContext(ioDispatcher) { apiService.getGroupMembersInfo(groupId, userIds) }

internal suspend fun GroupManager.getGroupOwnerAndAdmin(
    apiService: ImApiService,
    groupId: String,
): List<GroupMemberInfo> = withContext(ioDispatcher) {
    apiService.getGroupMembers(groupId).filter {
        it.roleLevel == GroupRoleLevel.OWNER || it.roleLevel == GroupRoleLevel.ADMIN
    }
}

internal suspend fun GroupManager.setGroupMemberInfo(
    apiService: ImApiService,
    groupId: String,
    userId: String,
    nickname: String? = null,
    faceURL: String? = null,
) = withContext(ioDispatcher) {
    apiService.setGroupMemberInfo(groupId, userId, nickname, faceURL)
}

internal suspend fun GroupManager.transferGroupOwner(
    apiService: ImApiService,
    groupId: String,
    newOwnerUserId: String,
) = withContext(ioDispatcher) {
    apiService.transferGroup(groupId, newOwnerUserId)
}

internal suspend fun GroupManager.changeGroupMute(
    apiService: ImApiService,
    groupId: String,
    isMute: Boolean,
) = withContext(ioDispatcher) {
    if (isMute) apiService.muteGroup(groupId) else apiService.cancelMuteGroup(groupId)
}

internal suspend fun GroupManager.changeGroupMemberMute(
    apiService: ImApiService,
    groupId: String,
    userId: String,
    mutedSeconds: Long,
) = withContext(ioDispatcher) {
    if (mutedSeconds > 0) {
        apiService.muteGroupMember(groupId, userId, mutedSeconds)
    } else {
        apiService.cancelMuteGroupMember(groupId, userId)
    }
}

// ─── UserManager extensions ──────────────────────────────────────────────────

internal suspend fun UserManager.getUsersInfoWithCache(
    databaseService: DatabaseService,
    userIds: List<String>,
): List<UserInfo> = withContext(ioDispatcher) {
    if (userIds.isEmpty()) return@withContext emptyList()
    val cached = databaseService.getAllUsers().filter { it.userID in userIds }
    val cachedIds = cached.map { it.userID }.toSet()
    val missing = userIds.filter { it !in cachedIds }
    if (missing.isEmpty()) return@withContext cached
    val remote = getUsersInfo(missing)
    cached + remote
}

internal suspend fun UserManager.getUsersInfoFromSrv(userIds: List<String>): List<UserInfo> =
    getUsersInfo(userIds)

internal suspend fun UserManager.getSelfUserInfo(
    databaseService: DatabaseService,
    loginUserId: LoginUserIdProvider,
): UserInfo? = withContext(ioDispatcher) {
    val userId = loginUserId() ?: return@withContext null
    databaseService.getAllUsers().find { it.userID == userId }
        ?: getUsersInfo(listOf(userId)).firstOrNull()
}

internal suspend fun UserManager.subscribeUsersStatus(
    apiService: ImApiService,
    loginUserId: LoginUserIdProvider,
    userIds: List<String>,
): List<UserStatusInfo> = withContext(ioDispatcher) {
    apiService.subscribeUsersStatus(loginUserId.requireUserId(), userIds, genre = 1)
}

internal suspend fun UserManager.unsubscribeUsersStatus(
    apiService: ImApiService,
    loginUserId: LoginUserIdProvider,
    userIds: List<String>,
) = withContext(ioDispatcher) {
    apiService.subscribeUsersStatus(loginUserId.requireUserId(), userIds, genre = 2)
}

internal suspend fun UserManager.getSubscribeUsersStatus(
    apiService: ImApiService,
    loginUserId: LoginUserIdProvider,
): List<UserStatusInfo> = withContext(ioDispatcher) {
    apiService.getSubscribeUsersStatus(loginUserId.requireUserId())
}

internal suspend fun UserManager.getUserStatus(
    apiService: ImApiService,
    loginUserId: LoginUserIdProvider,
    userIds: List<String>,
): List<UserStatusInfo> = withContext(ioDispatcher) {
    apiService.getUserStatus(loginUserId.requireUserId(), userIds)
}

internal suspend fun UserManager.getUserClientConfig(
    apiService: ImApiService,
    loginUserId: LoginUserIdProvider,
): Map<String, String> = withContext(ioDispatcher) {
    apiService.getUserClientConfig(loginUserId.requireUserId())
}

internal suspend fun UserManager.searchFriendInfo(apiService: ImApiService, keyword: String): List<FriendInfo> =
    withContext(ioDispatcher) { apiService.searchFriendInfo(keyword) }

internal suspend fun UserManager.searchUserFullInfo(apiService: ImApiService, keyword: String): List<UserFullInfo> =
    withContext(ioDispatcher) { apiService.searchUserFullInfo(keyword) }

internal suspend fun UserManager.getRtcToken(apiService: ImApiService, roomId: String, userId: String) =
    withContext(ioDispatcher) { apiService.getRtcToken(roomId, userId) }

internal suspend fun UserManager.resetPaymentPassword(
    apiService: ImApiService,
    verifyCode: String,
    newPaymentPassword: String,
    areaCode: String? = null,
    phoneNumber: String? = null,
    email: String? = null,
) = withContext(ioDispatcher) {
    apiService.resetPaymentPassword(
        verifyCode = verifyCode,
        newPaymentPassword = newPaymentPassword,
        areaCode = areaCode,
        phoneNumber = phoneNumber,
        email = email,
    )
}

// ─── FavoriteManager extensions ──────────────────────────────────────────────

internal suspend fun FavoriteManager.fetchFavoriteListFromServer(
    apiService: ImApiService,
    pageNumber: Int = 1,
    showNumber: Int = 20,
): FavoriteListResponse = withContext(ioDispatcher) {
    apiService.fetchFavoriteListFromServer(pageNumber, showNumber)
}

internal suspend fun FavoriteManager.syncFromServer(
    apiService: ImApiService,
    pageNumber: Int = 1,
    showNumber: Int = 100,
): List<FavoriteItem> = withContext(ioDispatcher) {
    apiService.fetchFavoriteListFromServer(pageNumber, showNumber).favorites
}

internal suspend fun FavoriteManager.isFavorited(
    apiService: ImApiService,
    targetType: String,
    targetId: String,
): Boolean = withContext(ioDispatcher) {
    apiService.fetchFavoriteListFromServer(1, 200).favorites
        .any { it.targetType == targetType && it.targetID == targetId }
}

internal suspend fun FavoriteManager.isMessageFavorited(apiService: ImApiService, clientMsgId: String): Boolean =
    isFavorited(apiService, "message", clientMsgId)

internal suspend fun FavoriteManager.isMomentFavorited(apiService: ImApiService, momentId: String): Boolean =
    isFavorited(apiService, FavoriteType.MOMENT_CONTENT.value, momentId)

internal suspend fun FavoriteManager.addMessage(
    apiService: ImApiService,
    eventEmitter: SdkEventEmitter,
    message: Message,
): FavoriteItem? = withContext(ioDispatcher) {
    val clientMsgId = message.clientMsgID ?: return@withContext null
    addFavoriteItem(
        apiService,
        eventEmitter,
        FavoriteType.MESSAGE.value,
        clientMsgId,
        kotlinx.serialization.json.Json.encodeToString(message),
    )
}

internal suspend fun FavoriteManager.removeMessage(apiService: ImApiService, eventEmitter: SdkEventEmitter, clientMsgId: String) =
    removeFavoriteItem(apiService, eventEmitter, FavoriteType.MESSAGE.value, clientMsgId)

internal suspend fun FavoriteManager.addMoment(
    apiService: ImApiService,
    eventEmitter: SdkEventEmitter,
    moment: MomentInfo,
) = addFavoriteItem(
    apiService,
    eventEmitter,
    FavoriteType.MOMENT_CONTENT.value,
    moment.momentID,
    kotlinx.serialization.json.Json.encodeToString(moment),
)

internal suspend fun FavoriteManager.removeMoment(apiService: ImApiService, eventEmitter: SdkEventEmitter, momentId: String) =
    removeFavoriteItem(apiService, eventEmitter, FavoriteType.MOMENT_CONTENT.value, momentId)

internal suspend fun FavoriteManager.addMomentComment(
    apiService: ImApiService,
    eventEmitter: SdkEventEmitter,
    comment: MomentCommentWithUser,
): FavoriteItem? = addFavoriteItem(
    apiService,
    eventEmitter,
    FavoriteType.MOMENT_COMMENT.value,
    comment.commentID,
    kotlinx.serialization.json.Json.encodeToString(comment),
)

internal suspend fun FavoriteManager.removeMomentComment(
    apiService: ImApiService,
    eventEmitter: SdkEventEmitter,
    commentID: String,
): Boolean = withContext(ioDispatcher) {
    removeFavoriteItem(apiService, eventEmitter, FavoriteType.MOMENT_COMMENT.value, commentID)
    true
}

internal suspend fun FavoriteManager.addNote(
    apiService: ImApiService,
    eventEmitter: SdkEventEmitter,
    title: String,
    content: String,
): FavoriteItem? = withContext(ioDispatcher) {
    val noteId = "note_${com.kurban.xuehuaim.sdk.util.System.currentTimeMillis()}"
    val data = kotlinx.serialization.json.Json.encodeToString(
        mapOf(
            "noteID" to noteId,
            "summary" to title,
            "content" to content,
            "createdAt" to com.kurban.xuehuaim.sdk.util.System.currentTimeMillis().toString(),
        ),
    )
    addFavoriteItem(apiService, eventEmitter, FavoriteType.NOTE.value, noteId, data)
}

internal suspend fun FavoriteManager.updateNote(
    apiService: ImApiService,
    eventEmitter: SdkEventEmitter,
    favoriteId: String,
    data: String,
) = updateFavorite(apiService, eventEmitter, favoriteId, data)

internal suspend fun FavoriteManager.addLink(
    apiService: ImApiService,
    eventEmitter: SdkEventEmitter,
    linkId: String,
    data: String,
) = addFavoriteItem(apiService, eventEmitter, "link", linkId, data)

internal suspend fun FavoriteManager.updateFavorite(
    apiService: ImApiService,
    eventEmitter: SdkEventEmitter,
    favoriteId: String,
    data: String,
) = withContext(ioDispatcher) {
    val updated = FavoriteItem(
        favoriteID = favoriteId,
        userID = "",
        targetType = "note",
        targetID = favoriteId,
        data = data,
    )
    val saved = apiService.addFavorite(updated)
    eventEmitter.emitFavorite(FavoriteEvent.Updated(saved))
    saved
}

internal suspend fun FavoriteManager.removeFavoriteItem(
    apiService: ImApiService,
    eventEmitter: SdkEventEmitter,
    targetType: String,
    targetId: String,
) = withContext(ioDispatcher) {
    apiService.removeFavoriteByTarget(targetType, targetId)
    eventEmitter.emitFavorite(FavoriteEvent.Deleted(targetId))
}

private suspend fun FavoriteManager.addFavoriteItem(
    apiService: ImApiService,
    eventEmitter: SdkEventEmitter,
    targetType: String,
    targetId: String,
    data: String?,
): FavoriteItem = withContext(ioDispatcher) {
    val item = FavoriteItem(
        favoriteID = "",
        userID = "",
        targetType = targetType,
        targetID = targetId,
        data = data,
    )
    addFavorite(item)
}

// ─── MomentsManager extensions ───────────────────────────────────────────────

internal suspend fun MomentsManager.handleNotification(
    eventEmitter: SdkEventEmitter,
    key: String,
    data: Map<String, String>,
) = withContext(ioDispatcher) {
    when (key) {
        "moment_created" -> data["moment"]?.let {
            runCatching {
                Json.decodeFromString<MomentInfo>(it)
            }.getOrNull()?.let { moment ->
                eventEmitter.emitMoments(MomentsEvent.NewMoment(moment))
            }
        }
        "moment_liked" -> {
            val momentId = data["momentID"] ?: return@withContext
            val like = MomentLike(
                userID = data["userID"].orEmpty(),
                nickname = data["nickname"],
                createTime = data["createTime"],
            )
            eventEmitter.emitMoments(MomentsEvent.Liked(momentId, like))
        }
        "moment_commented" -> {
            val momentId = data["momentID"] ?: return@withContext
            val comment = MomentComment(
                commentID = data["commentID"].orEmpty(),
                userID = data["userID"].orEmpty(),
                content = data["content"],
                createTime = data["createTime"],
            )
            eventEmitter.emitMoments(MomentsEvent.Commented(momentId, comment))
        }
    }
}

// ─── ReportAppealManager extensions ──────────────────────────────────────────

internal suspend fun ReportAppealManager.reportUser(
    apiService: ImApiService,
    targetUserId: String,
    category: String,
    description: String? = null,
    evidenceUrls: List<String>? = null,
): CreateReportResult? = withContext(ioDispatcher) {
    runCatching {
        apiService.createReport("user", targetUserId, category, description, evidenceUrls = evidenceUrls)
    }.getOrNull()
}

internal suspend fun ReportAppealManager.reportGroup(
    apiService: ImApiService,
    targetGroupId: String,
    category: String,
    description: String? = null,
    evidenceUrls: List<String>? = null,
): CreateReportResult? = withContext(ioDispatcher) {
    runCatching {
        apiService.createReport("group", targetGroupId, category, description, evidenceUrls = evidenceUrls)
    }.getOrNull()
}

internal suspend fun ReportAppealManager.reportMessage(
    apiService: ImApiService,
    targetUserId: String,
    messageId: String,
    category: String,
    description: String? = null,
    evidenceUrls: List<String>? = null,
): CreateReportResult? = withContext(ioDispatcher) {
    runCatching {
        apiService.createReport(
            "message",
            targetUserId,
            category,
            description,
            messageID = messageId,
            evidenceUrls = evidenceUrls,
        )
    }.getOrNull()
}

internal suspend fun ReportAppealManager.getAppealCaptcha(apiService: ImApiService): AppealCaptcha? =
    withContext(ioDispatcher) { runCatching { apiService.requestAppealCaptcha() }.getOrNull() }

internal suspend fun ReportAppealManager.uploadAppealEvidence(
    apiService: ImApiService,
    appealToken: String,
    bytes: ByteArray,
    fileName: String,
): AppealUploadResult? = withContext(ioDispatcher) {
    runCatching { apiService.uploadAppealEvidence(appealToken, bytes, fileName) }.getOrNull()
}

// ─── ApplicationManager extensions ───────────────────────────────────────────

internal suspend fun ApplicationManager.getLatestVersion(
    apiService: ImApiService,
    platform: String,
    currentVersion: String? = null,
): ApplicationVersionInfo? = withContext(ioDispatcher) {
    apiService.getLatestApplicationVersion(platform, currentVersion)
}

internal suspend fun ApplicationManager.getLatestVersion(
    apiService: ImApiService,
    currentVersion: String? = null,
): ApplicationVersionInfo? =
    getLatestVersion(apiService, applicationPlatformName(), currentVersion)

private fun LoginUserIdProvider.requireUserId(): String =
    this() ?: throw com.kurban.xuehuaim.sdk.exception.XueHuaException.from(
        com.kurban.xuehuaim.sdk.enum.SdkErrorCode.NOT_LOGIN,
    )
