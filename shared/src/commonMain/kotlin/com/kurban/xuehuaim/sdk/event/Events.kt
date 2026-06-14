package com.kurban.xuehuaim.sdk.event

import com.kurban.xuehuaim.sdk.model.BlacklistInfo
import com.kurban.xuehuaim.sdk.model.CallSession
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
import com.kurban.xuehuaim.sdk.model.MomentComment
import com.kurban.xuehuaim.sdk.model.MomentInfo
import com.kurban.xuehuaim.sdk.model.MomentLike
import com.kurban.xuehuaim.sdk.model.RedPacketInfo

sealed interface ConnectionEvent {
    data class ConnectFailed(val code: Int, val error: String) : ConnectionEvent
    data object Connecting : ConnectionEvent
    data object ConnectSuccess : ConnectionEvent
    data class KickedOffline(val reason: String) : ConnectionEvent
    data object TokenExpired : ConnectionEvent
    data object TokenInvalid : ConnectionEvent
    data object LogoutForced : ConnectionEvent
}

sealed interface MessageEvent {
    data class Received(val message: Message) : MessageEvent
    data class OnlineOnlyReceived(val message: Message) : MessageEvent
    data class Revoked(val conversationId: String, val clientMsgId: String) : MessageEvent
    data class Deleted(val conversationId: String, val clientMsgId: String) : MessageEvent
    data class ReadReceipt(val conversationId: String, val msgIdList: List<String>) : MessageEvent
    data class SendProgress(val clientMsgId: String, val progress: Int) : MessageEvent
    data class SendSuccess(val message: Message) : MessageEvent
    data class SendFailed(val clientMsgId: String, val code: Int, val error: String) : MessageEvent
    data class TypingStatusChanged(val data: InputStatusChangedData) : MessageEvent
}

sealed interface ConversationEvent {
    data class Changed(val conversation: ConversationInfo) : ConversationEvent
    data class TotalUnreadChanged(val count: Int) : ConversationEvent
    data class SyncStarted(val reinstalled: Boolean = false) : ConversationEvent
    data class SyncProgress(val progress: Int) : ConversationEvent
    data class SyncFinished(val count: Int, val reinstalled: Boolean = false) : ConversationEvent
    data class SyncFailed(val error: String) : ConversationEvent
}

sealed interface ConversationSyncState {
    data object Idle : ConversationSyncState
    data class Syncing(val progress: Int, val reinstalled: Boolean = false) : ConversationSyncState
    data class Finished(val count: Int, val reinstalled: Boolean = false) : ConversationSyncState
    data class Failed(val error: String) : ConversationSyncState
}

sealed interface FriendshipEvent {
    data class FriendAdded(val friend: FriendInfo) : FriendshipEvent
    data class FriendDeleted(val userId: String) : FriendshipEvent
    data class FriendInfoChanged(val friend: FriendInfo) : FriendshipEvent
    data class FriendApplicationAdded(val application: FriendApplicationInfo) : FriendshipEvent
    data class FriendApplicationAccepted(val application: FriendApplicationInfo) : FriendshipEvent
    data class FriendApplicationRejected(val application: FriendApplicationInfo) : FriendshipEvent
    data class BlackAdded(val black: BlacklistInfo) : FriendshipEvent
    data class BlackDeleted(val userId: String) : FriendshipEvent
}

sealed interface GroupEvent {
    data class GroupInfoChanged(val group: GroupInfo) : GroupEvent
    data class GroupDismissed(val groupId: String) : GroupEvent
    data class MemberAdded(val member: GroupMemberInfo) : GroupEvent
    data class MemberDeleted(val groupId: String, val userId: String) : GroupEvent
    data class MemberInfoChanged(val member: GroupMemberInfo) : GroupEvent
    data class JoinApplicationAdded(val groupId: String, val userId: String) : GroupEvent
    data class JoinApplicationAccepted(val groupId: String, val userId: String) : GroupEvent
    data class JoinApplicationRejected(val groupId: String, val userId: String) : GroupEvent
    data class GroupApplicationAdded(val application: GroupApplicationInfo) : GroupEvent
    data class GroupApplicationAccepted(val application: GroupApplicationInfo) : GroupEvent
    data class GroupApplicationRejected(val application: GroupApplicationInfo) : GroupEvent
}

sealed interface UserEvent {
    data class SelfInfoUpdated(val user: com.kurban.xuehuaim.sdk.model.UserInfo) : UserEvent
    data class UserStatusChanged(val userId: String, val status: String) : UserEvent
    data class OnlineStatusChanged(val userId: String, val platformIds: List<Int>) : UserEvent
}

sealed interface CallEvent {
    data class IncomingCall(val session: CallSession) : CallEvent
    data class CallAccepted(val session: CallSession) : CallEvent
    data class CallRejected(val sessionId: String) : CallEvent
    data class CallEnded(val sessionId: String, val reason: String? = null) : CallEvent
    data class CallBusy(val session: CallSession, val userId: String) : CallEvent
    data class SignalReceived(val signal: CallSignalElem) : CallEvent
}

sealed interface RedPacketEvent {
    data class Received(val packet: RedPacketInfo) : RedPacketEvent
    data class Grabbed(val packetId: String, val amount: Double) : RedPacketEvent
    data class Expired(val packetId: String) : RedPacketEvent
    data class PointsBalanceChanged(val balance: Double) : RedPacketEvent
}

sealed interface MomentsEvent {
    data class NewMoment(val moment: MomentInfo) : MomentsEvent
    data class MomentDeleted(val momentId: String) : MomentsEvent
    data class Liked(val momentId: String, val like: MomentLike) : MomentsEvent
    data class Unliked(val momentId: String, val userId: String) : MomentsEvent
    data class Commented(val momentId: String, val comment: MomentComment) : MomentsEvent
    data class CommentDeleted(val momentId: String, val commentId: String) : MomentsEvent
    data class ListUpdated(val moments: List<MomentInfo>) : MomentsEvent
}

sealed interface FavoriteEvent {
    data class Added(val item: FavoriteItem) : FavoriteEvent
    data class Deleted(val favoriteId: String) : FavoriteEvent
    data class Updated(val item: FavoriteItem) : FavoriteEvent
}

data class UploadProgressEvent(
    val uploadId: String,
    val progress: Int,
    val total: Long,
    val current: Long,
)

sealed interface CustomBusinessEvent {
    data class Received(val key: String, val data: String) : CustomBusinessEvent
}

sealed interface ServiceEvent {
    data class BackgroundPush(val data: String) : ServiceEvent
}
