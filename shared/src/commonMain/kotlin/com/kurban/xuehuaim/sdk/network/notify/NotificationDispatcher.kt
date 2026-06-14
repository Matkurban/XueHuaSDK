package com.kurban.xuehuaim.sdk.network.notify

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.enum.MessageType
import com.kurban.xuehuaim.sdk.event.CallEvent
import com.kurban.xuehuaim.sdk.event.ConversationEvent
import com.kurban.xuehuaim.sdk.event.CustomBusinessEvent
import com.kurban.xuehuaim.sdk.event.FavoriteEvent
import com.kurban.xuehuaim.sdk.event.FriendshipEvent
import com.kurban.xuehuaim.sdk.event.GroupEvent
import com.kurban.xuehuaim.sdk.event.MessageEvent
import com.kurban.xuehuaim.sdk.event.MomentsEvent
import com.kurban.xuehuaim.sdk.event.RedPacketEvent
import com.kurban.xuehuaim.sdk.event.UserEvent
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.model.BlacklistInfo
import com.kurban.xuehuaim.sdk.model.CallSignalElem
import com.kurban.xuehuaim.sdk.model.ConversationInfo
import com.kurban.xuehuaim.sdk.model.FavoriteItem
import com.kurban.xuehuaim.sdk.model.FriendApplicationInfo
import com.kurban.xuehuaim.sdk.model.FriendInfo
import com.kurban.xuehuaim.sdk.model.GroupApplicationInfo
import com.kurban.xuehuaim.sdk.model.GroupInfo
import com.kurban.xuehuaim.sdk.model.GroupMemberInfo
import com.kurban.xuehuaim.sdk.model.InputStatusChangedData
import com.kurban.xuehuaim.sdk.model.Message
import com.kurban.xuehuaim.sdk.model.MomentCommentWithUser
import com.kurban.xuehuaim.sdk.model.MomentInfo
import com.kurban.xuehuaim.sdk.model.MomentLikeWithUser
import com.kurban.xuehuaim.sdk.model.RedPacketInfo
import com.kurban.xuehuaim.sdk.model.UserInfo
import com.kurban.xuehuaim.sdk.network.http.ImApiService
import com.kurban.xuehuaim.sdk.platform.ioDispatcher
import com.kurban.xuehuaim.sdk.sync.ConversationDisplayEnricher
import com.kurban.xuehuaim.sdk.sync.FriendSync
import com.kurban.xuehuaim.sdk.sync.GroupSync
import com.kurban.xuehuaim.sdk.util.SdkLogger
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer

internal class NotificationDispatcher(
    private val databaseService: DatabaseService,
    private val apiService: ImApiService,
    private val eventEmitter: SdkEventEmitter,
    private val loginUserId: () -> String? = { null },
) {
    private val log = SdkLogger.tag("NotificationDispatcher")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun dispatch(message: Message) = withContext(ioDispatcher) {
        when (message.contentType) {
            MessageType.TYPING -> handleTyping(message)
            MessageType.FRIEND_APPLICATION -> handleFriendApplication(message)
            MessageType.FRIEND_APPLICATION_APPROVED -> handleFriendApplicationAccepted(message)
            MessageType.FRIEND_APPLICATION_REJECTED -> handleFriendApplicationRejected(message)
            MessageType.FRIEND_ADDED -> handleFriendAdded(message)
            MessageType.FRIEND_REMARK_SET,
            MessageType.FRIEND_INFO_UPDATED,
            MessageType.FRIENDS_INFO_UPDATE,
                -> handleFriendInfoUpdated(message)

            MessageType.FRIEND_DELETED -> handleFriendDeleted(message)
            MessageType.BLACK_ADDED -> handleBlackAdded(message)
            MessageType.BLACK_DELETED -> handleBlackDeleted(message)
            MessageType.CONVERSATION_CHANGE -> handleConversationChange(message)
            MessageType.USER_INFO_UPDATED -> handleUserInfoUpdated(message)
            MessageType.GROUP_CREATED,
            MessageType.GROUP_INFO_SET,
            MessageType.GROUP_INFO_SET_NAME,
            MessageType.GROUP_INFO_SET_ANNOUNCEMENT -> handleGroupInfoChanged(message)

            MessageType.GROUP_DISMISSED -> handleGroupDismissed(message)
            MessageType.MEMBER_ENTER,
            MessageType.MEMBER_INVITED -> handleMemberAdded(message)

            MessageType.MEMBER_KICKED,
            MessageType.MEMBER_QUIT -> handleMemberDeleted(message)

            MessageType.GROUP_MEMBER_INFO_SET,
            MessageType.GROUP_MEMBER_MUTED,
            MessageType.GROUP_MEMBER_CANCEL_MUTED,
            MessageType.GROUP_MEMBER_SET_TO_ADMIN,
            MessageType.GROUP_MEMBER_SET_TO_ORDINARY,
                -> handleMemberInfoChanged(message)

            MessageType.GROUP_OWNER_TRANSFERRED,
            MessageType.GROUP_MUTED,
            MessageType.GROUP_CANCEL_MUTED,
                -> handleGroupInfoChanged(message)

            MessageType.JOIN_GROUP_APPLICATION -> handleJoinGroupApplication(message)
            MessageType.GROUP_APPLICATION_ACCEPTED -> handleGroupApplicationAccepted(message)
            MessageType.GROUP_APPLICATION_REJECTED -> handleGroupApplicationRejected(message)
            MessageType.MSG_REVOKE -> handleMsgRevoke(message)
            MessageType.MSG_DELETE -> handleMsgDelete(message)
            MessageType.MSG_HAS_READ_RECEIPT -> handleReadReceipt(message)
            MessageType.BURN_AFTER_READING -> handleBurnAfterReading(message)
            MessageType.BUSINESS_NOTIFICATION -> handleBusinessNotification(message)
            MessageType.CALL_SIGNAL -> handleCallSignal(message)
            MessageType.RED_PACKET -> handleRedPacket(message)
            MessageType.RED_PACKET_GRAB_NOTIFY -> handleRedPacketGrab(message)
            else -> log.debug { "unhandled notification: ${message.contentType}" }
        }
    }

    suspend fun dispatchTyping(message: Message, conversationId: String) {
        handleTyping(message, conversationId)
    }

    private suspend fun handleTyping(message: Message, conversationId: String? = null) {
        val convId = conversationId ?: message.conversationID ?: return
        val sendId = message.sendID ?: return
        val contentMap = parseContent<Map<String, String>>(message.content)
        val msgTips = contentMap?.get("msgTips").orEmpty()
        val platformId = message.platformID ?: 0
        val platformIds =
            if (msgTips == "yes" && platformId > 0) listOf(platformId) else emptyList()
        eventEmitter.emitMessage(
            MessageEvent.TypingStatusChanged(
                InputStatusChangedData(
                    conversationID = convId,
                    userID = sendId,
                    platformIDs = platformIds,
                ),
            ),
        )
    }

    private suspend fun handleBusinessNotification(message: Message) {
        val detail = parseContent<Map<String, String>>(message.content) ?: return
        val key = detail["key"] ?: return
        val dataStr = detail["data"] ?: return
        when {
            key.startsWith("moment_") -> handleMomentNotification(key, dataStr)
            key.startsWith("favorite_") -> handleFavoriteNotification(key, dataStr)
            key.startsWith("red_packet_") || key.startsWith("points_") ->
                handleRedPacketBusinessNotification(key, dataStr)

            else -> eventEmitter.emitCustomBusiness(CustomBusinessEvent.Received(key, dataStr))
        }
    }

    private suspend fun handleMomentNotification(key: String, dataStr: String) {
        val data = parseJsonMap(dataStr) ?: return
        when (key) {
            "moment_created" -> parseContent<MomentInfo>(dataStr)?.let { moment ->
                databaseService.insertOrReplaceMoment(moment)
                eventEmitter.emitMoments(MomentsEvent.NewMoment(moment))
            }

            "moment_deleted" -> {
                val momentId = data["momentID"]?.jsonPrimitive?.content ?: return
                databaseService.deleteMoment(momentId)
                eventEmitter.emitMoments(MomentsEvent.MomentDeleted(momentId))
            }

            "moment_liked" -> {
                val momentId = data["momentID"]?.jsonPrimitive?.content ?: return
                val like = parseNestedContent<MomentLikeWithUser>(data, "like")
                    ?: MomentLikeWithUser(
                        momentID = momentId,
                        userID = data["userID"]?.jsonPrimitive?.content.orEmpty(),
                        createTime = data["createTime"]?.jsonPrimitive?.content,
                    )
                databaseService.getMomentById(momentId)?.let { moment ->
                    val likes = moment.likes.toMutableList().apply {
                        removeAll { it.userID == like.userID }
                        add(like)
                    }
                    databaseService.insertOrReplaceMoment(
                        moment.copy(
                            likes = likes,
                            likeCount = likes.size
                        )
                    )
                }
                eventEmitter.emitMoments(MomentsEvent.Liked(momentId, like))
            }

            "moment_unliked" -> {
                val momentId = data["momentID"]?.jsonPrimitive?.content ?: return
                val userId = data["userID"]?.jsonPrimitive?.content.orEmpty()
                databaseService.getMomentById(momentId)?.let { moment ->
                    val likes = moment.likes.filterNot { it.userID == userId }
                    databaseService.insertOrReplaceMoment(
                        moment.copy(
                            likes = likes,
                            likeCount = likes.size
                        )
                    )
                }
                eventEmitter.emitMoments(MomentsEvent.Unliked(momentId, userId))
            }

            "moment_commented" -> {
                val momentId = data["momentID"]?.jsonPrimitive?.content ?: return
                val comment = parseNestedContent<MomentCommentWithUser>(data, "comment")
                    ?: MomentCommentWithUser(
                        commentID = data["commentID"]?.jsonPrimitive?.content.orEmpty(),
                        momentID = momentId,
                        userID = data["userID"]?.jsonPrimitive?.content.orEmpty(),
                        content = data["content"]?.jsonPrimitive?.content,
                        createTime = data["createTime"]?.jsonPrimitive?.content,
                    )
                databaseService.getMomentById(momentId)?.let { moment ->
                    val comments = moment.comments.toMutableList().apply {
                        if (none { it.commentID == comment.commentID }) add(comment)
                    }
                    databaseService.insertOrReplaceMoment(
                        moment.copy(comments = comments, commentCount = comments.size),
                    )
                }
                eventEmitter.emitMoments(MomentsEvent.Commented(momentId, comment))
            }

            "moment_comment_deleted" -> {
                val momentId = data["momentID"]?.jsonPrimitive?.content ?: return
                val commentId = data["commentID"]?.jsonPrimitive?.content ?: return
                databaseService.getMomentById(momentId)?.let { moment ->
                    val comments = moment.comments.filterNot { it.commentID == commentId }
                    databaseService.insertOrReplaceMoment(
                        moment.copy(comments = comments, commentCount = comments.size),
                    )
                }
                eventEmitter.emitMoments(MomentsEvent.CommentDeleted(momentId, commentId))
            }

            else -> eventEmitter.emitCustomBusiness(CustomBusinessEvent.Received(key, dataStr))
        }
    }

    private suspend fun handleFavoriteNotification(key: String, dataStr: String) {
        when (key) {
            "favorite_added" -> parseContent<FavoriteItem>(dataStr)?.let {
                eventEmitter.emitFavorite(FavoriteEvent.Added(it))
            }

            "favorite_removed" -> {
                val data = parseJsonMap(dataStr) ?: return
                val favoriteId = data["favoriteID"]?.jsonPrimitive?.content ?: return
                eventEmitter.emitFavorite(FavoriteEvent.Deleted(favoriteId))
            }

            else -> eventEmitter.emitCustomBusiness(CustomBusinessEvent.Received(key, dataStr))
        }
    }

    private suspend fun handleRedPacketBusinessNotification(key: String, dataStr: String) {
        when (key) {
            "red_packet_expired" -> {
                val packetId = runCatching {
                    parseJsonMap(dataStr)?.get("packetID")?.jsonPrimitive?.content
                }.getOrNull() ?: dataStr.trim('"')
                eventEmitter.emitRedPacket(RedPacketEvent.Expired(packetId))
            }

            else -> eventEmitter.emitCustomBusiness(CustomBusinessEvent.Received(key, dataStr))
        }
    }

    private suspend fun handleUserInfoUpdated(message: Message) {
        val userId = parseContent<Map<String, String>>(message.content)?.get("userID") ?: return
        if (userId == loginUserId()) {
            val user = databaseService.getAllUsers().find { it.userID == userId }
                ?: UserInfo(userID = userId)
            eventEmitter.emitUser(UserEvent.SelfInfoUpdated(user))
        }
    }

    private suspend fun handleBurnAfterReading(message: Message) {
        handleConversationChange(message)
    }

    private suspend fun handleFriendApplication(message: Message) {
        val detail = parseContent<FriendApplicationInfo>(message.content) ?: return
        eventEmitter.emitFriendship(FriendshipEvent.FriendApplicationAdded(detail))
        syncFriendsIfLoggedIn()
    }

    private suspend fun handleFriendApplicationAccepted(message: Message) {
        val detail = parseContent<FriendApplicationInfo>(message.content) ?: return
        eventEmitter.emitFriendship(FriendshipEvent.FriendApplicationAccepted(detail))
        syncFriendsIfLoggedIn()
    }

    private suspend fun handleFriendApplicationRejected(message: Message) {
        val detail = parseContent<FriendApplicationInfo>(message.content) ?: return
        eventEmitter.emitFriendship(FriendshipEvent.FriendApplicationRejected(detail))
        syncFriendsIfLoggedIn()
    }

    private suspend fun handleFriendAdded(message: Message) {
        val detail = parseContent<FriendInfo>(message.content) ?: return
        eventEmitter.emitFriendship(FriendshipEvent.FriendAdded(detail))
        syncFriendsIfLoggedIn()
        loginUserId()?.let { selfId ->
            ConversationDisplayEnricher.updateSingleChatDisplay(
                databaseService = databaseService,
                eventEmitter = eventEmitter,
                friend = detail,
                selfUserId = selfId,
            )
        }
    }

    private suspend fun handleFriendInfoUpdated(message: Message) {
        val detail = parseContent<FriendInfo>(message.content) ?: return
        eventEmitter.emitFriendship(FriendshipEvent.FriendInfoChanged(detail))
        syncFriendsIfLoggedIn()
        loginUserId()?.let { selfId ->
            ConversationDisplayEnricher.updateSingleChatDisplay(
                databaseService = databaseService,
                eventEmitter = eventEmitter,
                friend = detail,
                selfUserId = selfId,
            )
        }
    }

    private suspend fun handleFriendDeleted(message: Message) {
        val userId = parseContent<Map<String, String>>(message.content)?.get("userID") ?: return
        eventEmitter.emitFriendship(FriendshipEvent.FriendDeleted(userId))
        syncFriendsIfLoggedIn()
    }

    private suspend fun handleBlackAdded(message: Message) {
        val detail = parseContent<BlacklistInfo>(message.content) ?: return
        eventEmitter.emitFriendship(FriendshipEvent.BlackAdded(detail))
        loginUserId()?.let { userId ->
            FriendSync.syncBlackList(apiService, databaseService, userId)
        }
    }

    private suspend fun handleBlackDeleted(message: Message) {
        val userId = parseContent<Map<String, String>>(message.content)?.get("userID") ?: return
        eventEmitter.emitFriendship(FriendshipEvent.BlackDeleted(userId))
        loginUserId()?.let { id ->
            FriendSync.syncBlackList(apiService, databaseService, id)
        }
    }

    private suspend fun handleConversationChange(message: Message) {
        val detail = parseContent<ConversationInfo>(message.content) ?: return
        databaseService.insertOrReplaceConversation(detail)
        eventEmitter.emitConversation(ConversationEvent.Changed(detail))
    }

    private suspend fun handleGroupInfoChanged(message: Message) {
        val detail = parseContent<GroupInfo>(message.content)
            ?: parseContent<Map<String, String>>(message.content)?.let { map ->
                map["group"]?.let { parseContent<GroupInfo>(it) }
            }
            ?: return
        eventEmitter.emitGroup(GroupEvent.GroupInfoChanged(detail))
        ConversationDisplayEnricher.updateGroupChatDisplay(
            databaseService = databaseService,
            eventEmitter = eventEmitter,
            group = detail,
        )
        syncGroupIfLoggedIn(detail.groupID)
    }

    private suspend fun handleGroupDismissed(message: Message) {
        val groupId = parseContent<Map<String, String>>(message.content)?.get("groupID") ?: return
        eventEmitter.emitGroup(GroupEvent.GroupDismissed(groupId))
        syncGroupIfLoggedIn(groupId)
    }

    private suspend fun handleMemberAdded(message: Message) {
        val detail = parseContent<GroupMemberInfo>(message.content)
            ?: parseContent<Map<String, String>>(message.content)?.let { map ->
                map["entrantUser"]?.let { parseContent<GroupMemberInfo>(it) }
                    ?: map["invitedUserList"]?.let { parseContent<GroupMemberInfo>(it) }
            }
            ?: return
        eventEmitter.emitGroup(GroupEvent.MemberAdded(detail))
        syncGroupIfLoggedIn(detail.groupID)
    }

    private suspend fun handleMemberDeleted(message: Message) {
        val map = parseContent<Map<String, String>>(message.content) ?: return
        val groupId = map["groupID"] ?: return
        val userId = map["userID"] ?: return
        eventEmitter.emitGroup(GroupEvent.MemberDeleted(groupId, userId))
        syncGroupIfLoggedIn(groupId)
    }

    private suspend fun handleMemberInfoChanged(message: Message) {
        val detail = parseContent<GroupMemberInfo>(message.content)
            ?: parseContent<Map<String, String>>(message.content)?.let { map ->
                map["changedUser"]?.let { parseContent<GroupMemberInfo>(it) }
                    ?: map["mutedUser"]?.let { parseContent<GroupMemberInfo>(it) }
            }
            ?: return
        eventEmitter.emitGroup(GroupEvent.MemberInfoChanged(detail))
        syncGroupIfLoggedIn(detail.groupID)
    }

    private suspend fun handleJoinGroupApplication(message: Message) {
        val detail = parseContent<GroupApplicationInfo>(message.content) ?: return
        eventEmitter.emitGroup(GroupEvent.GroupApplicationAdded(detail))
        val groupId = detail.groupID ?: return
        val userId = detail.userID ?: return
        eventEmitter.emitGroup(GroupEvent.JoinApplicationAdded(groupId, userId))
    }

    private suspend fun handleGroupApplicationAccepted(message: Message) {
        val detail = parseContent<GroupApplicationInfo>(message.content) ?: return
        eventEmitter.emitGroup(GroupEvent.GroupApplicationAccepted(detail))
        val groupId = detail.groupID ?: return
        val userId = detail.userID ?: return
        eventEmitter.emitGroup(GroupEvent.JoinApplicationAccepted(groupId, userId))
    }

    private suspend fun handleGroupApplicationRejected(message: Message) {
        val detail = parseContent<GroupApplicationInfo>(message.content) ?: return
        eventEmitter.emitGroup(GroupEvent.GroupApplicationRejected(detail))
        val groupId = detail.groupID ?: return
        val userId = detail.userID ?: return
        eventEmitter.emitGroup(GroupEvent.JoinApplicationRejected(groupId, userId))
    }

    private suspend fun handleMsgRevoke(message: Message) {
        val map = parseContent<Map<String, String>>(message.content) ?: return
        val conversationId = map["conversationID"] ?: message.conversationID ?: return
        val clientMsgId = map["clientMsgID"] ?: return
        databaseService.deleteMessage(clientMsgId)
        eventEmitter.emitMessage(MessageEvent.Revoked(conversationId, clientMsgId))
    }

    private suspend fun handleMsgDelete(message: Message) {
        val map = parseContent<Map<String, String>>(message.content) ?: return
        val conversationId = map["conversationID"] ?: message.conversationID ?: return
        val clientMsgId = map["clientMsgID"] ?: return
        databaseService.deleteMessage(clientMsgId)
        eventEmitter.emitMessage(MessageEvent.Deleted(conversationId, clientMsgId))
    }

    private suspend fun handleReadReceipt(message: Message) {
        val map = parseContent<Map<String, String>>(message.content) ?: return
        val conversationId = map["conversationID"] ?: return
        val msgIds = map["msgIDList"]?.split(",") ?: emptyList()
        eventEmitter.emitMessage(MessageEvent.ReadReceipt(conversationId, msgIds))
    }

    private suspend fun handleCallSignal(message: Message) {
        val detail = parseContent<CallSignalElem>(message.content) ?: return
        eventEmitter.emitCall(CallEvent.SignalReceived(detail))
    }

    private suspend fun handleRedPacket(message: Message) {
        val detail = parseContent<RedPacketInfo>(message.content) ?: return
        eventEmitter.emitRedPacket(RedPacketEvent.Received(detail))
    }

    private suspend fun handleRedPacketGrab(message: Message) {
        val map = parseContent<Map<String, String>>(message.content) ?: return
        val packetId = map["packetID"] ?: return
        val amount = map["amount"]?.toDoubleOrNull() ?: 0.0
        eventEmitter.emitRedPacket(RedPacketEvent.Grabbed(packetId, amount))
        map["balance"]?.toDoubleOrNull()?.let { balance ->
            eventEmitter.emitRedPacket(RedPacketEvent.PointsBalanceChanged(balance))
        }
    }

    private suspend fun syncFriendsIfLoggedIn() {
        val userId = loginUserId() ?: return
        FriendSync.syncFriends(apiService, databaseService, eventEmitter, userId)
    }

    private suspend fun syncGroupIfLoggedIn(groupId: String) {
        if (groupId.isBlank()) return
        GroupSync.syncGroupInfoAndMembers(apiService, databaseService, groupId)
    }

    private fun parseJsonMap(content: String): JsonObject? =
        runCatching { json.parseToJsonElement(content) as? JsonObject }.getOrNull()

    private inline fun <reified T> parseContent(content: String?): T? {
        if (content.isNullOrBlank()) return null
        return try {
            json.decodeFromString<T>(content)
        } catch (e: Exception) {
            log.error(e) { "parse notification failed" }
            null
        }
    }

    private inline fun <reified T> parseNestedContent(data: JsonObject, key: String): T? {
        val nested = data[key] ?: return null
        return try {
            json.decodeFromJsonElement(serializer<T>(), nested)
        } catch (e: Exception) {
            log.error(e) { "parse nested notification failed: $key" }
            null
        }
    }
}
