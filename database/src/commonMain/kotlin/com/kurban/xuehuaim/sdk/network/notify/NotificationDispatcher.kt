package com.kurban.xuehuaim.sdk.network.notify

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.enum.MessageType
import com.kurban.xuehuaim.sdk.event.CallEvent
import com.kurban.xuehuaim.sdk.event.ConversationEvent
import com.kurban.xuehuaim.sdk.event.FriendshipEvent
import com.kurban.xuehuaim.sdk.event.GroupEvent
import com.kurban.xuehuaim.sdk.event.MessageEvent
import com.kurban.xuehuaim.sdk.event.RedPacketEvent
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.model.BlacklistInfo
import com.kurban.xuehuaim.sdk.model.CallSignalElem
import com.kurban.xuehuaim.sdk.model.ConversationInfo
import com.kurban.xuehuaim.sdk.model.FriendApplicationInfo
import com.kurban.xuehuaim.sdk.model.FriendInfo
import com.kurban.xuehuaim.sdk.model.GroupApplicationInfo
import com.kurban.xuehuaim.sdk.model.GroupInfo
import com.kurban.xuehuaim.sdk.model.GroupMemberInfo
import com.kurban.xuehuaim.sdk.model.Message
import com.kurban.xuehuaim.sdk.model.RedPacketInfo
import com.kurban.xuehuaim.sdk.platform.ioDispatcher
import com.kurban.xuehuaim.sdk.sync.ConversationDisplayEnricher
import com.kurban.xuehuaim.sdk.util.SdkLogger
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

internal class NotificationDispatcher(
    private val databaseService: DatabaseService,
    private val eventEmitter: SdkEventEmitter,
    private val loginUserId: () -> String? = { null },
) {
    private val log = SdkLogger.tag("NotificationDispatcher")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun dispatch(message: Message) = withContext(ioDispatcher) {
        when (message.contentType) {
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
            MessageType.GROUP_CREATED,
            MessageType.GROUP_INFO_SET,
            MessageType.GROUP_INFO_SET_NAME,
            MessageType.GROUP_INFO_SET_ANNOUNCEMENT -> handleGroupInfoChanged(message)

            MessageType.GROUP_DISMISSED -> handleGroupDismissed(message)
            MessageType.MEMBER_ENTER,
            MessageType.MEMBER_INVITED -> handleMemberAdded(message)

            MessageType.MEMBER_KICKED,
            MessageType.MEMBER_QUIT -> handleMemberDeleted(message)

            MessageType.GROUP_MEMBER_INFO_SET -> handleMemberInfoChanged(message)
            MessageType.JOIN_GROUP_APPLICATION -> handleJoinGroupApplication(message)
            MessageType.GROUP_APPLICATION_ACCEPTED -> handleGroupApplicationAccepted(message)
            MessageType.GROUP_APPLICATION_REJECTED -> handleGroupApplicationRejected(message)
            MessageType.MSG_REVOKE -> handleMsgRevoke(message)
            MessageType.MSG_DELETE -> handleMsgDelete(message)
            MessageType.MSG_HAS_READ_RECEIPT -> handleReadReceipt(message)
            MessageType.CALL_SIGNAL -> handleCallSignal(message)
            MessageType.RED_PACKET -> handleRedPacket(message)
            MessageType.RED_PACKET_GRAB_NOTIFY -> handleRedPacketGrab(message)
            else -> log.debug { "unhandled notification: ${message.contentType}" }
        }
    }

    private suspend fun handleFriendApplication(message: Message) {
        val detail = parseContent<FriendApplicationInfo>(message.content) ?: return
        eventEmitter.emitFriendship(FriendshipEvent.FriendApplicationAdded(detail))
    }

    private suspend fun handleFriendApplicationAccepted(message: Message) {
        val detail = parseContent<FriendApplicationInfo>(message.content) ?: return
        eventEmitter.emitFriendship(FriendshipEvent.FriendApplicationAccepted(detail))
    }

    private suspend fun handleFriendApplicationRejected(message: Message) {
        val detail = parseContent<FriendApplicationInfo>(message.content) ?: return
        eventEmitter.emitFriendship(FriendshipEvent.FriendApplicationRejected(detail))
    }

    private suspend fun handleFriendAdded(message: Message) {
        val detail = parseContent<FriendInfo>(message.content) ?: return
        eventEmitter.emitFriendship(FriendshipEvent.FriendAdded(detail))
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
    }

    private suspend fun handleBlackAdded(message: Message) {
        val detail = parseContent<BlacklistInfo>(message.content) ?: return
        eventEmitter.emitFriendship(FriendshipEvent.BlackAdded(detail))
    }

    private suspend fun handleBlackDeleted(message: Message) {
        val userId = parseContent<Map<String, String>>(message.content)?.get("userID") ?: return
        eventEmitter.emitFriendship(FriendshipEvent.BlackDeleted(userId))
    }

    private suspend fun handleConversationChange(message: Message) {
        val detail = parseContent<ConversationInfo>(message.content) ?: return
        databaseService.insertOrReplaceConversation(detail)
        eventEmitter.emitConversation(ConversationEvent.Changed(detail))
    }

    private suspend fun handleGroupInfoChanged(message: Message) {
        val detail = parseContent<GroupInfo>(message.content) ?: return
        eventEmitter.emitGroup(GroupEvent.GroupInfoChanged(detail))
        ConversationDisplayEnricher.updateGroupChatDisplay(
            databaseService = databaseService,
            eventEmitter = eventEmitter,
            group = detail,
        )
    }

    private suspend fun handleGroupDismissed(message: Message) {
        val groupId = parseContent<Map<String, String>>(message.content)?.get("groupID") ?: return
        eventEmitter.emitGroup(GroupEvent.GroupDismissed(groupId))
    }

    private suspend fun handleMemberAdded(message: Message) {
        val detail = parseContent<GroupMemberInfo>(message.content) ?: return
        eventEmitter.emitGroup(GroupEvent.MemberAdded(detail))
    }

    private suspend fun handleMemberDeleted(message: Message) {
        val map = parseContent<Map<String, String>>(message.content) ?: return
        val groupId = map["groupID"] ?: return
        val userId = map["userID"] ?: return
        eventEmitter.emitGroup(GroupEvent.MemberDeleted(groupId, userId))
    }

    private suspend fun handleMemberInfoChanged(message: Message) {
        val detail = parseContent<GroupMemberInfo>(message.content) ?: return
        eventEmitter.emitGroup(GroupEvent.MemberInfoChanged(detail))
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
    }

    private inline fun <reified T> parseContent(content: String?): T? {
        if (content.isNullOrBlank()) return null
        return try {
            json.decodeFromString<T>(content)
        } catch (e: Exception) {
            log.error(e) { "parse notification failed" }
            null
        }
    }
}
