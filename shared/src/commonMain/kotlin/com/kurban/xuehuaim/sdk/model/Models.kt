package com.kurban.xuehuaim.sdk.model

import com.kurban.xuehuaim.sdk.enum.CallState
import com.kurban.xuehuaim.sdk.enum.CallType
import com.kurban.xuehuaim.sdk.enum.ConversationType
import com.kurban.xuehuaim.sdk.enum.GroupAtType
import com.kurban.xuehuaim.sdk.enum.GroupRoleLevel
import com.kurban.xuehuaim.sdk.enum.GroupType
import com.kurban.xuehuaim.sdk.enum.MessageStatus
import com.kurban.xuehuaim.sdk.enum.MessageType
import com.kurban.xuehuaim.sdk.enum.ReceiveMessageOpt
import com.kurban.xuehuaim.sdk.util.FlexibleNullableTimestampSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

@Serializable
data class UserInfo(
    val userID: String,
    val nickname: String? = null,
    val faceURL: String? = null,
    val ex: String? = null,
    val createTime: Long? = null,
    val remark: String? = null,
    val globalRecvMsgOpt: ReceiveMessageOpt? = null,
    val appMangerLevel: Int? = null,
) {
    fun getShowName(): String = remark?.takeIf { it.isNotBlank() }
        ?: nickname?.takeIf { it.isNotBlank() }
        ?: userID
}

@Serializable
data class UserFullInfo(
    val userID: String = "",
    val password: String? = null,
    val account: String? = null,
    val phoneNumber: String? = null,
    val areaCode: String? = null,
    val email: String? = null,
    val nickname: String? = null,
    val faceURL: String? = null,
    val gender: Int? = null,
    val level: Int? = null,
    val birth: Long? = null,
    val allowAddFriend: Int? = null,
    val allowBeep: Int? = null,
    val allowVibration: Int? = null,
    val globalRecvMsgOpt: Int? = null,
    val registerType: Int? = null,
) {
    fun getShowName(): String = nickname?.takeIf { it.isNotBlank() } ?: userID
}

@Serializable
data class PointsTransaction(
    val txID: String = "",
    val userID: String = "",
    val amount: Double = 0.0,
    val txType: Int = 0,
    val relatedID: String = "",
    val remark: String = "",
    val createTime: Long = 0,
) {
    val isIncome: Boolean get() = txType in listOf(2, 3, 4)
    val isExpense: Boolean get() = txType in listOf(1, 5)
}

@Serializable
data class GroupInfo(
    val groupID: String,
    val groupName: String? = null,
    val notification: String? = null,
    val introduction: String? = null,
    val faceURL: String? = null,
    val ownerUserID: String? = null,
    val createTime: Long? = null,
    val memberCount: Int? = null,
    val status: Int? = null,
    val creatorUserID: String? = null,
    val groupType: GroupType? = null,
    val ex: String? = null,
    val needVerification: Int? = null,
    val lookMemberInfo: Int? = null,
    val applyMemberFriend: Int? = null,
)

@Serializable
data class GroupMemberInfo(
    val groupID: String,
    val userID: String,
    val nickname: String? = null,
    val faceURL: String? = null,
    val roleLevel: GroupRoleLevel? = null,
    val joinTime: Long? = null,
    val joinSource: Int? = null,
    val operatorUserID: String? = null,
    val ex: String? = null,
    val muteEndTime: Long? = null,
    val inviterUserID: String? = null,
)

@Serializable
data class ConversationInfo(
    val conversationID: String,
    val conversationType: ConversationType? = null,
    val userID: String? = null,
    val groupID: String? = null,
    val showName: String? = null,
    val faceURL: String? = null,
    val recvMsgOpt: ReceiveMessageOpt? = null,
    val unreadCount: Int = 0,
    val latestMsg: Message? = null,
    val latestMsgSendTime: Long? = null,
    val draftText: String? = null,
    val draftTextTime: Long? = null,
    val isPinned: Boolean? = null,
    val isPrivateChat: Boolean? = null,
    val burnDuration: Int? = null,
    val isMsgDestruct: Boolean? = null,
    val msgDestructTime: Long? = null,
    val ex: String? = null,
    val isNotInGroup: Boolean? = null,
    val groupAtType: GroupAtType? = null,
    val maxSeq: Long = 0,
    val minSeq: Long = 0,
    val hasReadSeq: Long = 0,
) {
    val isSingleChat: Boolean get() = conversationType == ConversationType.SINGLE
    val isGroupChat: Boolean get() = conversationType == ConversationType.SUPER_GROUP
    val isInGroup: Boolean get() = isNotInGroup != true
    val isValid: Boolean get() = isSingleChat || (isGroupChat && isInGroup)
}

@Serializable
data class Message(
    val clientMsgID: String,
    val serverMsgID: String? = null,
    val createTime: Long? = null,
    val sendTime: Long? = null,
    val sessionType: ConversationType? = null,
    val sendID: String? = null,
    val recvID: String? = null,
    val msgFrom: Int? = null,
    val contentType: MessageType? = null,
    val platformID: Int? = null,
    val senderNickname: String? = null,
    @SerialName("senderFaceURL")
    val senderFaceUrl: String? = null,
    val groupID: String? = null,
    val content: String? = null,
    val seq: Long = 0,
    val isRead: Boolean? = null,
    val status: MessageStatus? = null,
    val attachedInfo: String? = null,
    val ex: String? = null,
    val localEx: String? = null,
    val conversationID: String? = null,
    val textElem: TextElem? = null,
    val pictureElem: PictureElem? = null,
    val soundElem: SoundElem? = null,
    val videoElem: VideoElem? = null,
    val fileElem: FileElem? = null,
    val atTextElem: AtTextElem? = null,
    val quoteElem: QuoteElem? = null,
    val customElem: CustomElem? = null,
    val callSignalElem: CallSignalElem? = null,
    val redPacketElem: RedPacketElem? = null,
    val mergeElem: MergeElem? = null,
)

@Serializable
data class MergeElem(
    val title: String? = null,
    val abstractList: List<String>? = null,
    val multiMessage: List<Message>? = null,
)

@Serializable
data class TextElem(val content: String)

@Serializable
data class PictureElem(
    val sourcePath: String? = null,
    val sourcePicture: PictureInfo? = null,
    val bigPicture: PictureInfo? = null,
    val snapshotPicture: PictureInfo? = null
)

@Serializable
data class PictureInfo(
    val uuid: String? = null,
    val type: String? = null,
    val size: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val url: String? = null
)

@Serializable
data class SoundElem(
    val uuid: String? = null,
    val soundPath: String? = null,
    val sourceUrl: String? = null,
    val dataSize: Long? = null,
    val duration: Long? = null
)

@Serializable
data class VideoElem(
    val videoPath: String? = null,
    val videoUUID: String? = null,
    val videoUrl: String? = null,
    val videoType: String? = null,
    val videoSize: Long? = null,
    val duration: Long? = null,
    val snapshotPath: String? = null,
    val snapshotUUID: String? = null,
    val snapshotSize: Long? = null,
    val snapshotUrl: String? = null,
    val snapshotWidth: Int? = null,
    val snapshotHeight: Int? = null
)

@Serializable
data class FileElem(
    val filePath: String? = null,
    val uuid: String? = null,
    val sourceUrl: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null
)

@Serializable
data class AtTextElem(
    val text: String? = null,
    val atUserList: List<String>? = null,
    val atUsersInfo: List<AtUserInfo>? = null,
    val quoteMessage: Message? = null,
    val isAtSelf: Boolean? = null
)

@Serializable
data class AtUserInfo(val atUserID: String, val groupNickname: String? = null)

@Serializable
data class QuoteElem(val text: String? = null, val quoteMessage: Message? = null)

@Serializable
data class CustomElem(
    val data: String? = null,
    val extension: String? = null,
    val description: String? = null
)

@Serializable
data class FriendInfo(
    val ownerUserID: String,
    val userID: String,
    val nickname: String? = null,
    val faceURL: String? = null,
    val remark: String? = null,
    val ex: String? = null,
    val createTime: Long? = null,
    val addSource: Int? = null,
    val operatorUserID: String? = null,
    val isPinned: Boolean? = null,
) {
    fun getShowName(): String = remark?.takeIf { it.isNotBlank() }
        ?: nickname?.takeIf { it.isNotBlank() }
        ?: userID
}

@Serializable
data class FriendApplicationInfo(
    val fromUserID: String,
    val fromNickname: String? = null,
    val fromFaceURL: String? = null,
    val toUserID: String,
    val toNickname: String? = null,
    val toFaceURL: String? = null,
    val handleResult: Int? = null,
    val reqMsg: String? = null,
    val createTime: Long? = null,
    val handlerUserID: String? = null,
    val handleMsg: String? = null,
    val handleTime: Long? = null,
    val ex: String? = null,
)

@Serializable
data class GroupApplicationInfo(
    val groupID: String? = null,
    val groupName: String? = null,
    val notification: String? = null,
    val introduction: String? = null,
    @SerialName("groupFaceURL") val groupFaceURL: String? = null,
    val createTime: Long? = null,
    val status: Int? = null,
    val creatorUserID: String? = null,
    val groupType: Int? = null,
    val ownerUserID: String? = null,
    val memberCount: Int? = null,
    val userID: String? = null,
    val nickname: String? = null,
    @SerialName("userFaceURL") val userFaceURL: String? = null,
    val gender: Int? = null,
    val handleResult: Int? = null,
    val reqMsg: String? = null,
    @SerialName("handledMsg") val handledMsg: String? = null,
    val reqTime: Long? = null,
    val handleUserID: String? = null,
    val handledTime: Long? = null,
    val ex: String? = null,
    val joinSource: Int? = null,
    val inviterUserID: String? = null,
)

@Serializable
data class BlacklistInfo(
    val ownerUserID: String,
    val blockUserID: String,
    val userID: String? = null,
    val nickname: String? = null,
    val faceURL: String? = null,
    val createTime: Long? = null,
    val ex: String? = null,
)

@Serializable
data class AuthCacheData(
    val userID: String,
    val imToken: String,
    val chatToken: String? = null,
    val nickname: String? = null,
    val faceURL: String? = null,
)

@Serializable
data class ApiResponse<T>(
    val errCode: Int = 0,
    val errMsg: String = "",
    val errDlt: String? = null,
    val data: T? = null,
) {
    val isSuccess: Boolean get() = errCode == 0
}

@Serializable
data class SearchResult(
    val messageList: List<Message> = emptyList(),
    val conversationList: List<ConversationInfo> = emptyList(),
)

@Serializable
data class MomentMedia(
    val type: String = "",
    val url: String = "",
    @SerialName("coverURL") val coverUrl: String? = null,
    val duration: Int? = null,
    val extra: String? = null,
)

object NullableMomentMediaListSerializer : KSerializer<List<MomentMedia>> {
    private val delegate = ListSerializer(MomentMedia.serializer())

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): List<MomentMedia> {
        val jsonDecoder = decoder as? JsonDecoder
            ?: return decoder.decodeSerializableValue(delegate)
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonArray -> jsonDecoder.json.decodeFromJsonElement(delegate, element)
            JsonNull -> emptyList()
            else -> emptyList()
        }
    }

    override fun serialize(encoder: Encoder, value: List<MomentMedia>) {
        encoder.encodeSerializableValue(delegate, value)
    }
}

@Serializable
data class MomentInfo(
    val momentID: String,
    val userID: String,
    val content: String? = null,
    @Serializable(with = NullableMomentMediaListSerializer::class)
    val media: List<MomentMedia> = emptyList(),
    val visibleType: Int? = null,
    val createTime: String? = null,
    val likeCount: Int? = null,
    val commentCount: Int? = null,
    val likes: List<MomentLike> = emptyList(),
    val comments: List<MomentComment> = emptyList(),
)

@Serializable
data class MomentLike(
    val userID: String,
    val nickname: String? = null,
    val createTime: String? = null
)

@Serializable
data class MomentComment(
    val commentID: String,
    val userID: String,
    val content: String? = null,
    val createTime: String? = null
)

@Serializable
data class MomentCommentWithUser(
    val commentID: String,
    val momentID: String,
    val userID: String,
    val nickname: String? = null,
    val faceURL: String? = null,
    val content: String? = null,
    val createTime: String? = null,
)

@Serializable
data class MomentListResponse(
    val total: Int = 0,
    val moments: List<MomentInfo> = emptyList(),
) {
    companion object {
        fun empty() = MomentListResponse()
    }
}

@Serializable
data class FavoriteItem(
    val favoriteID: String,
    val userID: String,
    val targetType: String,
    val targetID: String,
    val data: String? = null,
    @Serializable(with = FlexibleNullableTimestampSerializer::class)
    val createTime: Long? = null,
)

@Serializable
data class CallSession(
    val roomID: String,
    val callType: CallType,
    val inviterUserID: String = "",
    val inviteeUserIDs: List<String> = emptyList(),
    val liveURL: String? = null,
    val token: String? = null,
    val state: CallState = CallState.IDLE,
    val createTime: Long? = null,
    val connectTime: Long? = null,
) {
    val sessionID: String get() = roomID
    val callerUserID: String get() = inviterUserID
    val calleeUserID: String get() = inviteeUserIDs.firstOrNull().orEmpty()
    val isGroupCall: Boolean get() = inviteeUserIDs.size > 1
}

@Serializable
data class CallSignaling(
    val action: String,
    val roomID: String,
    val callType: String,
    val inviterUserID: String = "",
    val inviteeUserIDs: List<String> = emptyList(),
    val liveURL: String? = null,
    val timeout: Int = 60,
    val timestamp: Long = 0,
    val extra: String? = null,
) {
    companion object {
        const val BUSINESS_ID = "rtc_signaling"
    }
}

@Serializable
data class CreateMeetingResult(
    val roomID: String,
    val token: String,
    val liveURL: String,
    val busyUsers: List<String> = emptyList(),
)

@Serializable
data class JoinMeetingResult(
    val token: String,
    val liveURL: String,
    val roomID: String,
    val callType: String = "audio",
)

@Serializable
data class CallSignalElem(
    val signalType: Int? = null,
    val sessionID: String? = null,
    val roomID: String? = null,
    val callType: CallType? = null,
    val data: JsonElement? = null,
)

@Serializable
data class RedPacketElem(
    val packetID: String? = null,
    val title: String? = null,
    val amount: Double? = null,
    val count: Int? = null,
    val packetType: Int? = null,
)

@Serializable
data class RedPacketInfo(
    val packetID: String,
    val senderUserID: String,
    val title: String? = null,
    val amount: Double? = null,
    val count: Int? = null,
    val grabbedCount: Int? = null,
    val status: Int? = null,
    val createTime: Long? = null,
)

@Serializable
data class ReportInfo(
    val reportID: String? = null,
    val reporterUserID: String,
    val reportedUserID: String? = null,
    val reportedGroupID: String? = null,
    val reason: String? = null,
    val content: String? = null,
    val createTime: Long? = null,
)

@Serializable
data class AppealInfo(
    val appealID: String? = null,
    val userID: String,
    val reason: String? = null,
    val content: String? = null,
    val status: Int? = null,
    val createTime: Long? = null,
)

@Serializable
data class ApplicationVersionInfo(
    val version: String,
    val forceUpdate: Boolean = false,
    val downloadUrl: String? = null,
    val releaseNotes: String? = null,
)

@Serializable
data class UploadFileResult(
    val url: String,
    val uuid: String? = null,
)

@Serializable
data class InputStatusChangedData(
    val conversationID: String,
    val userID: String,
    val platformIDs: List<Int> = emptyList(),
)

@Serializable
data class UserStatusInfo(
    val userID: String? = null,
    val status: Int? = null,
    val platformIDs: List<Int>? = null,
)

@Serializable
data class FavoriteListResponse(
    val total: Int = 0,
    val favorites: List<FavoriteItem> = emptyList(),
)

@Serializable
data class AppealCaptcha(
    val captchaID: String,
    val captchaImage: String? = null,
)

@Serializable
data class AppealUploadResult(
    val url: String,
)

@Serializable
data class CreateReportResult(
    val reportID: String? = null,
)

@Serializable
data class AdvancedTextElem(
    val text: String,
    val messageEntityList: List<MessageEntity> = emptyList(),
)

@Serializable
data class MessageEntity(
    val type: String? = null,
    val offset: Int = 0,
    val length: Int = 0,
    val url: String? = null,
    val info: String? = null,
)

@Serializable
data class SendRedPacketRequest(
    val packetType: Int,
    val totalAmount: Double,
    val totalCount: Int,
    val greeting: String,
    val convID: String,
    val targetUserID: String? = null,
)

@Serializable
data class RedPacketGrabInfo(
    val grabberID: String,
    val nickname: String = "",
    @SerialName("faceURL") val faceURL: String = "",
    val amount: Double,
    val createTime: Long,
)

@Serializable
data class RedPacketDetail(
    val packetID: String,
    val senderID: String,
    val senderNickname: String = "",
    @SerialName("senderFaceURL") val senderFaceURL: String = "",
    val packetType: Int,
    val totalAmount: Double,
    val totalCount: Int,
    val grabbedAmount: Double,
    val grabbedCount: Int,
    val status: Int,
    val greeting: String,
    val expireAt: Long,
    val grabs: List<RedPacketGrabInfo> = emptyList(),
    val myGrabAmount: Double = 0.0,
)

@Serializable
data class SearchParams(
    val conversationID: String? = null,
    val clientMsgIDList: List<String>? = null,
)

@Serializable
data class AdvancedMessage(
    val messageList: List<Message>? = null,
    val isEnd: Boolean? = null,
    val errCode: Int? = null,
    val errMsg: String? = null,
    val lastMinSeq: Long? = null,
)

@Serializable
data class SpaceInfo(
    val spaceName: String,
)

@Serializable
data class RevokedInfo(
    val revokerID: String? = null,
    val revokerRole: GroupRoleLevel? = null,
    val revokerNickname: String? = null,
    val clientMsgID: String? = null,
    val revokeTime: Long? = null,
    val sourceMessageSendTime: Long? = null,
    val sourceMessageSendID: String? = null,
    val sourceMessageSenderNickname: String? = null,
    val sessionType: ConversationType? = null,
)
