package com.kurban.xuehuaim.sdk.network.http

import com.kurban.xuehuaim.sdk.enum.IMPlatform
import com.kurban.xuehuaim.sdk.exception.XueHuaException
import com.kurban.xuehuaim.sdk.model.ApiResponse
import com.kurban.xuehuaim.sdk.model.AppealInfo
import com.kurban.xuehuaim.sdk.model.AppealCaptcha
import com.kurban.xuehuaim.sdk.model.AppealUploadResult
import com.kurban.xuehuaim.sdk.model.CreateReportResult
import com.kurban.xuehuaim.sdk.model.FavoriteListResponse
import com.kurban.xuehuaim.sdk.model.MomentComment
import com.kurban.xuehuaim.sdk.model.MomentLike
import com.kurban.xuehuaim.sdk.model.UserStatusInfo
import com.kurban.xuehuaim.sdk.model.ApplicationVersionInfo
import com.kurban.xuehuaim.sdk.model.BlacklistInfo
import com.kurban.xuehuaim.sdk.model.ConversationInfo
import com.kurban.xuehuaim.sdk.model.ConversationReq
import com.kurban.xuehuaim.sdk.model.CreateMeetingResult
import com.kurban.xuehuaim.sdk.model.FavoriteItem
import com.kurban.xuehuaim.sdk.model.FriendApplicationInfo
import com.kurban.xuehuaim.sdk.model.FriendInfo
import com.kurban.xuehuaim.sdk.model.GroupApplicationInfo
import com.kurban.xuehuaim.sdk.model.GroupInfo
import com.kurban.xuehuaim.sdk.model.GroupMemberInfo
import com.kurban.xuehuaim.sdk.model.JoinMeetingResult
import com.kurban.xuehuaim.sdk.model.Message
import com.kurban.xuehuaim.sdk.model.MomentInfo
import com.kurban.xuehuaim.sdk.model.MomentListResponse
import com.kurban.xuehuaim.sdk.model.PointsTransaction
import com.kurban.xuehuaim.sdk.model.RedPacketDetail
import com.kurban.xuehuaim.sdk.model.ReportInfo
import com.kurban.xuehuaim.sdk.model.SendRedPacketRequest
import com.kurban.xuehuaim.sdk.model.UserFullInfo
import com.kurban.xuehuaim.sdk.model.UserInfo
import com.kurban.xuehuaim.sdk.network.sync.PullMsgBySeqsReq
import com.kurban.xuehuaim.sdk.network.sync.PullMsgResp
import com.kurban.xuehuaim.sdk.network.sync.SeqRange
import com.kurban.xuehuaim.sdk.platform.currentPlatform
import com.kurban.xuehuaim.sdk.util.md5Hex
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal typealias LoginUserIdProvider = () -> String?

internal object ImApiRoutes {
    const val USER_TOKEN = "/auth/user_token"
    const val PARSE_TOKEN = "/auth/parse_token"
    const val FORCE_LOGOUT = "/auth/force_logout"
    const val GET_USERS_INFO = "/user/get_users_info"
    const val SUBSCRIBE_USERS_STATUS = "/user/subscribe_users_status"
    const val GET_SUBSCRIBE_USERS_STATUS = "/user/get_subscribe_users_status"
    const val GET_USERS_STATUS = "/user/get_users_status"
    const val GET_USER_CLIENT_CONFIG = "/user/get_user_client_config"
    const val UPDATE_USER_INFO = "/user/update_user_info"
    const val GET_ALL_CONVERSATIONS = "/conversation/get_all_conversations"
    const val GET_INCREMENTAL_CONVERSATIONS = "/conversation/get_incremental_conversations"
    const val GET_CONVERSATIONS = "/conversation/get_conversations"
    const val SET_CONVERSATIONS = "/conversation/set_conversations"
    const val GET_FRIENDS = "/friend/get_friend_list"
    const val GET_INCREMENTAL_FRIENDS = "/friend/get_incremental_friends"
    const val ADD_FRIEND = "/friend/add_friend"
    const val DELETE_FRIEND = "/friend/delete_friend"
    const val GET_FRIEND_APPLICATIONS = "/friend/get_friend_apply_list"
    const val GET_SELF_FRIEND_APPLICATIONS = "/friend/get_self_friend_apply_list"
    const val GET_SELF_UNHANDLED_APPLY_COUNT = "/friend/get_self_unhandled_apply_count"
    const val GET_DESIGNATED_FRIENDS = "/friend/get_designated_friends"
    const val UPDATE_FRIENDS = "/friend/update_friends"
    const val ACCEPT_FRIEND = "/friend/add_friend_response"
    const val REFUSE_FRIEND = "/friend/add_friend_response"
    const val ADD_BLACK = "/friend/add_black"
    const val REMOVE_BLACK = "/friend/remove_black"
    const val GET_BLACK_LIST = "/friend/get_black_list"
    const val CREATE_GROUP = "/group/create_group"
    const val GET_JOINED_GROUPS = "/group/get_joined_group_list"
    const val GET_INCREMENTAL_JOIN_GROUP = "/group/get_incremental_join_groups"
    const val GET_GROUP_INFO = "/group/get_groups_info"
    const val SET_GROUP_INFO = "/group/set_group_info"
    const val SET_GROUP_INFO_EX = "/group/set_group_info_ex"
    const val DISMISS_GROUP = "/group/dismiss_group"
    const val JOIN_GROUP = "/group/join_group"
    const val GROUP_APPLICATION_RESPONSE = "/group/group_application_response"
    const val QUIT_GROUP = "/group/quit_group"
    const val GET_GROUP_MEMBERS = "/group/get_group_member_list"
    const val INVITE_TO_GROUP = "/group/invite_user_to_group"
    const val KICK_GROUP_MEMBER = "/group/kick_group"
    const val TRANSFER_GROUP = "/group/transfer_group"
    const val MUTE_GROUP_MEMBER = "/group/mute_group_member"
    const val CANCEL_MUTE_GROUP_MEMBER = "/group/cancel_mute_group_member"
    const val MUTE_GROUP = "/group/mute_group"
    const val CANCEL_MUTE_GROUP = "/group/cancel_mute_group"
    const val SET_GROUP_MEMBER_INFO = "/group/set_group_member_info"
    const val GET_GROUP_MEMBERS_INFO = "/group/get_group_members_info"
    const val GET_RECV_GROUP_APPLICATION_LIST = "/group/get_recv_group_applicationList"
    const val GET_SEND_GROUP_APPLICATION_LIST = "/group/get_user_req_group_applicationList"
    const val GET_GROUP_APPLICATION_UNHANDLED_COUNT = "/group/get_group_application_unhandled_count"
    const val SEND_MSG = "/msg/send_msg"
    const val REVOKE_MSG = "/msg/revoke_msg"
    const val DELETE_MSG = "/msg/delete_msgs"
    const val MARK_MSG_READ = "/msg/mark_msgs_as_read"
    const val MARK_CONVERSATION_AS_READ = "/msg/mark_conversation_as_read"
    const val GET_CONVERSATIONS_HAS_READ_AND_MAX_SEQ = "/msg/get_conversations_has_read_and_max_seq"
    const val CLEAR_CONVERSATION_MSG = "/msg/clear_conversation_msg"
    const val CLEAR_ALL_MSG = "/msg/user_clear_all_msg"
    const val GET_MAX_SEQ = "/msg/get_max_seq"
    const val PULL_BY_SEQ = "/msg/pull_msg_by_seq"
    const val SEARCH_MSG = "/msg/search_msg"
    const val FCM_UPDATE_TOKEN = "/third/fcm_update_token"
    const val UPLOAD_FILE = "/third/minio_upload"
    const val OBJECT_PART_LIMIT = "/object/part_limit"
    const val OBJECT_INITIATE_MULTIPART_UPLOAD = "/object/initiate_multipart_upload"
    const val OBJECT_AUTH_SIGN = "/object/auth_sign"
    const val OBJECT_COMPLETE_MULTIPART_UPLOAD = "/object/complete_multipart_upload"
}

internal object ChatApiRoutes {
    const val LOGIN = "/account/login"
    const val REGISTER = "/account/register"
    const val LOGIN_BY_EMAIL = "/account/login_by_email"
    const val LOGIN_BY_PHONE = "/account/login_by_phone"
    const val SEND_VERIFY_CODE = "/account/code/send"
    const val VERIFY_CODE = "/account/code/verify"
    const val RESET_PASSWORD = "/account/password/reset"
    const val CHANGE_PASSWORD = "/account/password/change"
    const val MOMENT_LIST = "/moment/list"
    const val MOMENT_CREATE = "/moment/create"
    const val MOMENT_DELETE = "/moment/delete"
    const val MOMENT_LIKE = "/moment/like"
    const val MOMENT_COMMENT = "/moment/comment"
    const val MOMENT_UNLIKE = "/moment/unlike"
    const val MOMENT_COMMENT_DELETE = "/moment/delete_comment"
    const val FAVORITE_LIST = "/favorite/list"
    const val FAVORITE_ADD = "/favorite/add"
    const val FAVORITE_DELETE = "/favorite/delete"
    const val MEETING_CREATE = "/meeting/create"
    const val MEETING_JOIN = "/meeting/join"
    const val MEETING_LEAVE = "/meeting/leave"
    const val MEETING_END = "/meeting/end"
    const val MEETING_GET = "/meeting/get"
    const val MEETING_GET_ACTIVE = "/meeting/get_active"
    const val RTC_GET_TOKEN = "/user/rtc/get_rtc_token"
    const val DELETE_ACCOUNT = "/account/delete"
    const val SEARCH_USER = "/user/search/full"
    const val SEARCH_FRIEND = "/friend/search"
    const val FAVORITE_REMOVE = "/favorite/remove"
    const val CREATE_REPORT = "/report/create"
    const val APPEAL_CAPTCHA = "/public/appeal/captcha"
    const val APPEAL_UPLOAD = "/public/appeal/upload"
    const val LATEST_APPLICATION_VERSION = "/application/latest_version"
    const val UPDATE_USER = "/user/update"
    const val PAYMENT_PASSWORD_SET = "/user/payment_password/set"
    const val PAYMENT_PASSWORD_CHANGE = "/user/payment_password/change"
    const val PAYMENT_PASSWORD_VERIFY = "/user/payment_password/verify"
    const val PAYMENT_PASSWORD_CHECK = "/user/payment_password/check"
    const val POINTS_BALANCE = "/user/points/balance"
    const val POINTS_TRANSACTIONS = "/points/transactions"
    const val GET_USER_FULL = "/user/find/full"
    const val PAYMENT_PASSWORD_RESET = "/user/payment_password/reset"
    const val RED_PACKET_SEND = "/red_packet/send"
    const val RED_PACKET_GRAB = "/red_packet/grab"
    const val RED_PACKET_DETAIL = "/red_packet/detail"
    const val REPORT_SUBMIT = "/report/submit"
    const val APP_VERSION = "/application/version"
}

internal object AdminApiRoutes {
    const val APPEAL_SUBMIT = "/public/appeal/submit"
    const val APPEAL_LIST = "/public/appeal/list"
    const val APPEAL_CAPTCHA = "/public/appeal/captcha"
    const val APPEAL_UPLOAD = "/public/appeal/upload"
}

@Serializable
internal data class UserTokenReq(
    @SerialName("secret") val secret: String,
    @SerialName("platformID") val platformID: Int,
    @SerialName("userID") val userID: String,
)

@Serializable
internal data class UserTokenResp(
    @SerialName("token") val token: String,
    @SerialName("expireTimeSeconds") val expireTimeSeconds: Long? = null,
)

@Serializable
internal data class LoginReq(
    @SerialName("account") val account: String? = null,
    @SerialName("password") val password: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("phoneNumber") val phoneNumber: String? = null,
    @SerialName("areaCode") val areaCode: String? = null,
    @SerialName("verifyCode") val verifyCode: String? = null,
    @SerialName("platform") val platform: Int,
)

@Serializable
internal data class LoginResp(
    @SerialName("userID") val userID: String,
    @SerialName("chatToken") val chatToken: String,
    @SerialName("imToken") val imToken: String? = null,
    @SerialName("nickname") val nickname: String? = null,
    @SerialName("faceURL") val faceURL: String? = null,
)

@Serializable
internal data class TokenReq(
    @SerialName("token") val token: String,
)

@Serializable
internal data class PartLimitResp(
    @SerialName("minPartSize") val minPartSize: Long = 5L * 1024 * 1024,
    @SerialName("maxPartSize") val maxPartSize: Long = 5L * 1024 * 1024 * 1024,
    @SerialName("maxNumSize") val maxNumSize: Int = 10000,
)

@Serializable
internal data class InitiateMultipartUploadReq(
    @SerialName("hash") val hash: String,
    @SerialName("size") val size: Long,
    @SerialName("partSize") val partSize: Long,
    @SerialName("maxParts") val maxParts: Int,
    @SerialName("cause") val cause: String = "",
    @SerialName("name") val name: String,
    @SerialName("contentType") val contentType: String,
)

@Serializable
internal data class MultipartUploadInfo(
    @SerialName("uploadID") val uploadID: String = "",
    @SerialName("partSize") val partSize: Long? = null,
    @SerialName("sign") val sign: UploadSignInfo? = null,
)

@Serializable
internal data class UploadSignInfo(
    @SerialName("url") val url: String = "",
    @SerialName("query") val query: List<UploadKeyValue> = emptyList(),
    @SerialName("header") val header: List<UploadKeyValue> = emptyList(),
    @SerialName("parts") val parts: List<UploadPartSign> = emptyList(),
)

@Serializable
internal data class UploadKeyValue(
    @SerialName("key") val key: String = "",
    @SerialName("values") val values: List<String> = emptyList(),
)

@Serializable
internal data class UploadPartSign(
    @SerialName("partNumber") val partNumber: Int = 0,
    @SerialName("url") val url: String = "",
    @SerialName("query") val query: List<UploadKeyValue> = emptyList(),
    @SerialName("header") val header: List<UploadKeyValue> = emptyList(),
)

@Serializable
internal data class InitiateMultipartUploadResp(
    @SerialName("url") val url: String? = null,
    @SerialName("upload") val upload: MultipartUploadInfo? = null,
)

@Serializable
internal data class AuthSignResp(
    @SerialName("parts") val parts: List<UploadPartSign> = emptyList(),
)

@Serializable
internal data class CompleteMultipartUploadReq(
    @SerialName("uploadID") val uploadID: String,
    @SerialName("parts") val parts: List<String>,
    @SerialName("name") val name: String,
    @SerialName("contentType") val contentType: String,
    @SerialName("cause") val cause: String = "",
)

@Serializable
internal data class CompleteMultipartUploadResp(
    @SerialName("url") val url: String = "",
)

@Serializable
internal data class SendMsgReq(
    @SerialName("recvID") val recvID: String = "",
    @SerialName("groupID") val groupID: String = "",
    @SerialName("senderNickname") val senderNickname: String? = null,
    @SerialName("senderFaceURL") val senderFaceURL: String? = null,
    @SerialName("senderPlatformID") val senderPlatformID: Int,
    @SerialName("content") val content: String,
    @SerialName("contentType") val contentType: Int,
    @SerialName("sessionType") val sessionType: Int,
    @SerialName("isOnlineOnly") val isOnlineOnly: Boolean = false,
    @SerialName("notOfflinePush") val notOfflinePush: Boolean = false,
    @SerialName("offlinePushInfo") val offlinePushInfo: String? = null,
    @SerialName("ex") val ex: String? = null,
    @SerialName("clientMsgID") val clientMsgID: String,
)

@Serializable
internal data class UserIdsReq(
    @SerialName("userIDs") val userIDs: List<String>,
)

@Serializable
internal data class CreateMeetingReq(
    @SerialName("creatorUserID") val creatorUserID: String,
    @SerialName("callType") val callType: String,
    @SerialName("inviteeUserIDs") val inviteeUserIDs: List<String>,
)

@Serializable
internal data class MeetingRoomReq(
    @SerialName("roomID") val roomID: String,
    @SerialName("userID") val userID: String,
)

@Serializable
internal data class RtcTokenReq(
    @SerialName("roomID") val roomID: String,
    @SerialName("userID") val userID: String,
)

@Serializable
internal data class RtcTokenResp(
    @SerialName("token") val token: String,
    @SerialName("liveURL") val liveURL: String? = null,
)

@Serializable
internal data class RegisterUserInfo(
    @SerialName("nickname") val nickname: String,
    @SerialName("faceURL") val faceURL: String? = null,
    @SerialName("birth") val birth: Long = 0,
    @SerialName("gender") val gender: Int = 1,
    @SerialName("email") val email: String? = null,
    @SerialName("areaCode") val areaCode: String? = null,
    @SerialName("phoneNumber") val phoneNumber: String? = null,
    @SerialName("account") val account: String? = null,
    @SerialName("password") val password: String,
)

@Serializable
internal data class RegisterReq(
    @SerialName("deviceID") val deviceID: String,
    @SerialName("verifyCode") val verifyCode: String,
    @SerialName("platform") val platform: Int,
    @SerialName("invitationCode") val invitationCode: String? = null,
    @SerialName("autoLogin") val autoLogin: Boolean = true,
    @SerialName("user") val user: RegisterUserInfo,
)

@Serializable
internal data class SendVerifyCodeReq(
    @SerialName("areaCode") val areaCode: String? = null,
    @SerialName("phoneNumber") val phoneNumber: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("usedFor") val usedFor: Int,
    @SerialName("invitationCode") val invitationCode: String? = null,
)

@Serializable
internal data class ResetPasswordReq(
    @SerialName("areaCode") val areaCode: String? = null,
    @SerialName("phoneNumber") val phoneNumber: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("verifyCode") val verifyCode: String,
    @SerialName("password") val password: String,
)

@Serializable
internal data class SearchUserReq(
    @SerialName("keyword") val keyword: String,
    @SerialName("pageNumber") val pageNumber: Int = 1,
    @SerialName("showNumber") val showNumber: Int = 20,
)

@Serializable
internal data class FriendApplyResponseReq(
    @SerialName("toUserID") val toUserID: String,
    @SerialName("handleResult") val handleResult: Int,
    @SerialName("handleMsg") val handleMsg: String = "",
)

@Serializable
internal data class AddFriendReq(
    @SerialName("toUserID") val toUserID: String,
    @SerialName("reqMsg") val reqMsg: String = "",
)

@Serializable
internal data class CreateGroupReq(
    @SerialName("memberUserIDs") val memberUserIDs: List<String>,
    @SerialName("groupInfo") val groupInfo: GroupInfo,
)

@Serializable
internal data class ChangePasswordReq(
    @SerialName("userID") val userID: String,
    @SerialName("currentPassword") val currentPassword: String,
    @SerialName("newPassword") val newPassword: String,
)

@Serializable
internal data class PointsBalanceResp(
    @SerialName("balance") val balance: Double = 0.0,
)

@Serializable
internal data class FindUserFullInfoReq(
    @SerialName("userIDs") val userIDs: List<String>,
    @SerialName("pagination") val pagination: Pagination = Pagination(1, 100),
    @SerialName("platform") val platform: Int = currentPlatform().value,
)

@Serializable
internal data class FindUserFullInfoResp(
    @SerialName("users") val users: List<UserFullInfo> = emptyList(),
)

@Serializable
internal data class UpdateChatUserInfoReq(
    @SerialName("userID") val userID: String,
    @SerialName("platform") val platform: Int = currentPlatform().value,
    @SerialName("account") val account: String? = null,
    @SerialName("phoneNumber") val phoneNumber: String? = null,
    @SerialName("areaCode") val areaCode: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("nickname") val nickname: String? = null,
    @SerialName("faceURL") val faceURL: String? = null,
    @SerialName("gender") val gender: Int? = null,
    @SerialName("birth") val birth: Long? = null,
)

@Serializable
internal data class PaymentPasswordSetReq(
    @SerialName("paymentPassword") val paymentPassword: String,
    @SerialName("loginPassword") val loginPassword: String,
)

@Serializable
internal data class PaymentPasswordChangeReq(
    @SerialName("currentPaymentPassword") val currentPaymentPassword: String,
    @SerialName("newPaymentPassword") val newPaymentPassword: String,
)

@Serializable
internal data class PaymentPasswordVerifyReq(
    @SerialName("paymentPassword") val paymentPassword: String,
)

@Serializable
internal data class PaymentPasswordCheckResp(
    @SerialName("isSet") val isSet: Boolean = false,
)

@Serializable
internal data class PointsTransactionsReq(
    @SerialName("pageNumber") val pageNumber: Int = 1,
    @SerialName("showNumber") val showNumber: Int = 20,
    @SerialName("txType") val txType: Int? = null,
)

@Serializable
internal data class PointsTransactionsResp(
    @SerialName("total") val total: Int = 0,
    @SerialName("transactions") val transactions: List<PointsTransaction> = emptyList(),
)

@Serializable
internal data class UsersInfoResp(
    @SerialName("usersInfo") val usersInfo: List<UserInfo> = emptyList(),
)

@Serializable
internal data class Pagination(
    @SerialName("pageNumber") val pageNumber: Int,
    @SerialName("showNumber") val showNumber: Int,
)

@Serializable
internal data class OwnerUserIDReq(
    @SerialName("ownerUserID") val ownerUserID: String,
)

@Serializable
internal data class UserPaginationReq(
    @SerialName("userID") val userID: String,
    @SerialName("pagination") val pagination: Pagination,
)

@Serializable
internal data class FromUserPaginationReq(
    @SerialName("fromUserID") val fromUserID: String,
    @SerialName("pagination") val pagination: Pagination,
)

@Serializable
internal data class GetAllConversationsResp(
    @SerialName("conversations") val conversations: List<ConversationInfo> = emptyList(),
)

@Serializable
internal data class IncrementalConversationReq(
    @SerialName("userID") val userID: String,
    @SerialName("version") val version: Int,
    @SerialName("versionID") val versionID: String,
)

@Serializable
internal data class IncrementalFriendsResp(
    @SerialName("full") val full: Boolean = false,
    @SerialName("versionID") val versionID: String = "",
    @SerialName("version") val version: Int = 0,
    @SerialName("delete") val delete: List<String>? = null,
    @SerialName("insert") val insert: List<FriendInfoDto>? = null,
    @SerialName("update") val update: List<FriendInfoDto>? = null,
)

@Serializable
internal data class IncrementalJoinGroupResp(
    @SerialName("full") val full: Boolean = false,
    @SerialName("versionID") val versionID: String = "",
    @SerialName("version") val version: Int = 0,
    @SerialName("delete") val delete: List<String>? = null,
    @SerialName("insert") val insert: List<GroupInfo>? = null,
    @SerialName("update") val update: List<GroupInfo>? = null,
)

@Serializable
internal data class IncrementalConversationResp(
    @SerialName("full") val full: Boolean = false,
    @SerialName("versionID") val versionID: String = "",
    @SerialName("version") val version: Int = 0,
    @SerialName("delete") val delete: List<String>? = null,
    @SerialName("insert") val insert: List<ConversationInfo>? = null,
    @SerialName("update") val update: List<ConversationInfo>? = null,
)

@Serializable
internal data class ConversationsHasReadAndMaxSeqReq(
    @SerialName("userID") val userID: String,
    @SerialName("conversationIDs") val conversationIDs: List<String>,
)

@Serializable
internal data class ConversationSeqInfo(
    @SerialName("maxSeq") val maxSeq: Long = 0,
    @SerialName("hasReadSeq") val hasReadSeq: Long = 0,
)

@Serializable
internal data class ConversationsHasReadAndMaxSeqResp(
    @SerialName("seqs") val seqs: Map<String, ConversationSeqInfo> = emptyMap(),
)

@Serializable
internal data class FriendInfoDto(
    @SerialName("ownerUserID") val ownerUserID: String? = null,
    @SerialName("userID") val userID: String? = null,
    @SerialName("friendUserID") val friendUserID: String? = null,
    @SerialName("nickname") val nickname: String? = null,
    @SerialName("faceURL") val faceURL: String? = null,
    @SerialName("remark") val remark: String? = null,
    @SerialName("ex") val ex: String? = null,
    @SerialName("createTime") val createTime: Long? = null,
    @SerialName("addSource") val addSource: Int? = null,
    @SerialName("operatorUserID") val operatorUserID: String? = null,
    @SerialName("isPinned") val isPinned: Boolean? = null,
    @SerialName("friendUser") val friendUser: UserInfo? = null,
) {
    fun toFriendInfo(fallbackOwnerUserId: String): FriendInfo = FriendInfo(
        ownerUserID = ownerUserID ?: fallbackOwnerUserId,
        userID = friendUser?.userID ?: friendUserID ?: userID.orEmpty(),
        nickname = friendUser?.nickname ?: nickname,
        faceURL = friendUser?.faceURL ?: faceURL,
        remark = remark ?: friendUser?.remark,
        ex = ex ?: friendUser?.ex,
        createTime = createTime ?: friendUser?.createTime,
        addSource = addSource,
        operatorUserID = operatorUserID,
        isPinned = isPinned,
    )
}

@Serializable
internal data class GetFriendListResp(
    @SerialName("friendsInfo") val friendsInfo: List<FriendInfoDto> = emptyList(),
)

@Serializable
internal data class GetJoinedGroupListResp(
    @SerialName("groups") val groups: List<GroupInfo> = emptyList(),
)

@Serializable
internal data class GroupIdsReq(
    @SerialName("groupIDs") val groupIDs: List<String>,
)

@Serializable
internal data class GetGroupsInfoResp(
    @SerialName("groupInfos") val groupInfos: List<GroupInfo> = emptyList(),
)

@Serializable
internal data class GetBlackListResp(
    @SerialName("blacks") val blacks: List<BlacklistInfo> = emptyList(),
)

@Serializable
internal data class GetFriendApplicationsResp(
    @SerialName("FriendRequests") val friendRequests: List<FriendApplicationInfo> = emptyList(),
)

@Serializable
internal data class FavoriteListReq(
    @SerialName("pageNumber") val pageNumber: Int,
    @SerialName("showNumber") val showNumber: Int,
)

@Serializable
internal data class FavoriteListResp(
    @SerialName("total") val total: Int = 0,
    @SerialName("favorites") val favorites: List<FavoriteItem> = emptyList(),
)

@Serializable
internal data class AppVersionReq(
    @SerialName("platform") val platform: String,
    @SerialName("version") val version: String? = null,
)

@Serializable
internal data class UserStatusListResp(
    @SerialName("statusList") val statusList: List<UserStatusInfo> = emptyList(),
)

@Serializable
internal data class UserClientConfigResp(
    @SerialName("config") val config: Map<String, String> = emptyMap(),
)

@Serializable
internal data class LatestAppVersionResp(
    @SerialName("version") val version: ApplicationVersionInfo? = null,
)

@Serializable
internal data class DeleteAccountReq(
    @SerialName("currentPassword") val currentPassword: String,
)

@Serializable
internal data class OwnerFriendReq(
    @SerialName("ownerUserID") val ownerUserID: String,
    @SerialName("friendUserID") val friendUserID: String,
)

@Serializable
internal data class OwnerBlackReq(
    @SerialName("ownerUserID") val ownerUserID: String,
    @SerialName("blackUserID") val blackUserID: String,
    @SerialName("ex") val ex: String = "",
)

@Serializable
internal data class MomentListReq(
    @SerialName("ownerUserID") val ownerUserID: String = "",
    @SerialName("pageNumber") val pageNumber: Int,
    @SerialName("showNumber") val showNumber: Int,
)

@Serializable
internal data class MomentListResp(
    @SerialName("total") val total: Int = 0,
    @SerialName("moments") val moments: List<MomentInfo> = emptyList(),
)

@Serializable
internal data class CreateMomentReq(
    @SerialName("content") val content: String,
    @SerialName("visibleType") val visibleType: Int = 1,
    @SerialName("extra") val extra: String = "",
)

@Serializable
internal data class CreateMomentResp(
    @SerialName("moment") val moment: MomentInfo? = null,
)

@Serializable
internal data class MomentLikeReq(
    @SerialName("momentID") val momentID: String,
    @SerialName("ownerUserID") val ownerUserID: String? = null,
)

@Serializable
internal data class MomentCommentReq(
    @SerialName("momentID") val momentID: String,
    @SerialName("content") val content: String,
    @SerialName("ownerUserID") val ownerUserID: String? = null,
)

@Serializable
internal data class MomentDeleteReq(
    @SerialName("momentID") val momentID: String,
)

@Serializable
internal data class MomentCommentDeleteReq(
    @SerialName("commentID") val commentID: String,
)

@Serializable
internal data class ServerGroupRequestUserDto(
    @SerialName("userID") val userID: String? = null,
    @SerialName("nickname") val nickname: String? = null,
    @SerialName("faceURL") val faceURL: String? = null,
)

@Serializable
internal data class ServerGroupRequestGroupDto(
    @SerialName("groupID") val groupID: String? = null,
    @SerialName("groupName") val groupName: String? = null,
    @SerialName("notification") val notification: String? = null,
    @SerialName("introduction") val introduction: String? = null,
    @SerialName("faceURL") val faceURL: String? = null,
    @SerialName("createTime") val createTime: Long? = null,
    @SerialName("status") val status: Int? = null,
    @SerialName("creatorUserID") val creatorUserID: String? = null,
    @SerialName("groupType") val groupType: Int? = null,
    @SerialName("ownerUserID") val ownerUserID: String? = null,
    @SerialName("memberCount") val memberCount: Int? = null,
)

@Serializable
internal data class ServerGroupRequestDto(
    @SerialName("userInfo") val userInfo: ServerGroupRequestUserDto? = null,
    @SerialName("groupInfo") val groupInfo: ServerGroupRequestGroupDto? = null,
    @SerialName("handleResult") val handleResult: Int? = null,
    @SerialName("reqMsg") val reqMsg: String? = null,
    @SerialName("handleMsg") val handledMsg: String? = null,
    @SerialName("reqTime") val reqTime: Long? = null,
    @SerialName("handleUserID") val handleUserID: String? = null,
    @SerialName("handleTime") val handledTime: Long? = null,
    @SerialName("ex") val ex: String? = null,
    @SerialName("joinSource") val joinSource: Int? = null,
    @SerialName("inviterUserID") val inviterUserID: String? = null,
)

@Serializable
internal data class GetGroupApplicationListResp(
    @SerialName("groupRequests") val groupRequests: List<ServerGroupRequestDto> = emptyList(),
)

@Serializable
internal data class GetGroupMemberListReq(
    @SerialName("groupID") val groupID: String,
    @SerialName("filter") val filter: Int = 0,
    @SerialName("pagination") val pagination: Pagination,
)

@Serializable
internal data class GetGroupMemberListResp(
    @SerialName("members") val members: List<GroupMemberInfo> = emptyList(),
)

@Serializable
internal data class RevokeMsgReq(
    @SerialName("userID") val userID: String,
    @SerialName("conversationID") val conversationID: String,
    @SerialName("seq") val seq: Long,
)

@Serializable
internal data class DeleteMsgsReq(
    @SerialName("userID") val userID: String,
    @SerialName("conversationID") val conversationID: String,
    @SerialName("seqs") val seqs: List<Long>,
)

@Serializable
internal data class MarkMsgsAsReadReq(
    @SerialName("userID") val userID: String,
    @SerialName("conversationID") val conversationID: String,
    @SerialName("seqs") val seqs: List<Long>,
)

@Serializable
internal data class SearchMsgReq(
    @SerialName("sendID") val sendID: String? = null,
    @SerialName("recvID") val recvID: String? = null,
    @SerialName("groupID") val groupID: String? = null,
    @SerialName("contentType") val contentType: Int? = null,
    @SerialName("keyword") val keyword: String = "",
    @SerialName("pagination") val pagination: Pagination,
)

@Serializable
internal data class SearchMsgResp(
    @SerialName("chatLogs") val chatLogs: List<Message> = emptyList(),
    @SerialName("chatLogsNum") val chatLogsNum: Int = 0,
)

@Serializable
internal data class JoinGroupReq(
    @SerialName("groupID") val groupID: String,
    @SerialName("reqMessage") val reqMessage: String = "",
    @SerialName("joinSource") val joinSource: Int = 3,
    @SerialName("inviterUserID") val inviterUserID: String = "",
    @SerialName("ex") val ex: String = "",
)

@Serializable
internal data class GroupApplicationResponseReq(
    @SerialName("groupID") val groupID: String,
    @SerialName("fromUserID") val fromUserID: String,
    @SerialName("handledMsg") val handledMsg: String,
    @SerialName("handleResult") val handleResult: Int,
)

@Serializable
internal data class GetGroupApplicationUnhandledCountReq(
    @SerialName("userID") val userID: String,
    @SerialName("time") val time: Long = 0,
)

@Serializable
internal data class GetGroupApplicationUnhandledCountResp(
    @SerialName("count") val count: Int = 0,
)

@Serializable
internal data class ResetPaymentPasswordReq(
    @SerialName("areaCode") val areaCode: String? = null,
    @SerialName("phoneNumber") val phoneNumber: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("verifyCode") val verifyCode: String,
    @SerialName("newPaymentPassword") val newPaymentPassword: String,
)

@Serializable
internal data class FcmUpdateTokenReq(
    @SerialName("platformID") val platformID: String,
    @SerialName("fcmToken") val fcmToken: String,
    @SerialName("account") val account: String,
    @SerialName("expireTime") val expireTime: Long = 0,
)

@Serializable
internal data class ForceLogoutReq(
    @SerialName("platformID") val platformID: Int,
    @SerialName("userID") val userID: String,
)

@Serializable
internal data class SendRedPacketResp(
    @SerialName("packetID") val packetID: String,
)

@Serializable
internal data class GrabRedPacketResp(
    @SerialName("amount") val amount: Double,
)

@Serializable
internal data class ClearAllMsgReq(
    @SerialName("userID") val userID: String,
)

internal fun ServerGroupRequestDto.toGroupApplicationInfo(): GroupApplicationInfo {
    val user = userInfo
    val group = groupInfo
    return GroupApplicationInfo(
        groupID = group?.groupID,
        groupName = group?.groupName,
        notification = group?.notification,
        introduction = group?.introduction,
        groupFaceURL = group?.faceURL,
        createTime = group?.createTime,
        status = group?.status,
        creatorUserID = group?.creatorUserID,
        groupType = group?.groupType,
        ownerUserID = group?.ownerUserID,
        memberCount = group?.memberCount,
        userID = user?.userID,
        nickname = user?.nickname,
        userFaceURL = user?.faceURL,
        handleResult = handleResult,
        reqMsg = reqMsg,
        handledMsg = handledMsg,
        reqTime = reqTime,
        handleUserID = handleUserID,
        handledTime = handledTime,
        ex = ex,
        joinSource = joinSource,
        inviterUserID = inviterUserID,
    )
}

internal fun applicationPlatformName(): String = when (currentPlatform()) {
    IMPlatform.IOS, IMPlatform.IPAD -> "ios"
    IMPlatform.ANDROID, IMPlatform.ANDROID_PAD -> "android"
    IMPlatform.WINDOWS -> "windows"
    IMPlatform.XOS -> "macos"
    IMPlatform.LINUX -> "linux"
    IMPlatform.WEB, IMPlatform.MINI_WEB -> "web"
}

internal class ImApiService(
    private val httpClient: SdkHttpClient,
) {
    suspend fun login(req: LoginReq): LoginResp =
        httpClient.chatPostEnvelope(ChatApiRoutes.LOGIN, req)

    suspend fun loginByEmail(req: LoginReq): LoginResp =
        httpClient.chatPostEnvelope(ChatApiRoutes.LOGIN_BY_EMAIL, req)

    suspend fun loginByPhone(req: LoginReq): LoginResp =
        httpClient.chatPostEnvelope(ChatApiRoutes.LOGIN, req)

    suspend fun parseToken(token: String): ApiResponse<Unit?> =
        httpClient.imPost(ImApiRoutes.PARSE_TOKEN, TokenReq(token))

    suspend fun getUsersInfo(userIDs: List<String>): List<UserInfo> {
        val resp: UsersInfoResp =
            httpClient.imPostEnvelope(ImApiRoutes.GET_USERS_INFO, UserIdsReq(userIDs))
        return resp.usersInfo
    }

    suspend fun sendMessage(req: SendMsgReq): Message =
        httpClient.imPostEnvelope(ImApiRoutes.SEND_MSG, req)

    suspend fun getAllConversations(ownerUserID: String): List<ConversationInfo> {
        val resp: GetAllConversationsResp = httpClient.imPostEnvelope(
            ImApiRoutes.GET_ALL_CONVERSATIONS,
            OwnerUserIDReq(ownerUserID),
        )
        return resp.conversations
    }

    suspend fun getIncrementalConversations(
        userID: String,
        version: Int,
        versionID: String,
    ): IncrementalConversationResp = httpClient.imPostEnvelope(
        ImApiRoutes.GET_INCREMENTAL_CONVERSATIONS,
        IncrementalConversationReq(userID = userID, version = version, versionID = versionID),
    )

    suspend fun getIncrementalFriends(
        userID: String,
        version: Int,
        versionID: String,
    ): IncrementalFriendsResp = httpClient.imPostEnvelope(
        ImApiRoutes.GET_INCREMENTAL_FRIENDS,
        IncrementalConversationReq(userID = userID, version = version, versionID = versionID),
    )

    suspend fun getIncrementalJoinGroup(
        userID: String,
        version: Int,
        versionID: String,
    ): IncrementalJoinGroupResp = httpClient.imPostEnvelope(
        ImApiRoutes.GET_INCREMENTAL_JOIN_GROUP,
        IncrementalConversationReq(userID = userID, version = version, versionID = versionID),
    )

    suspend fun getConversationsHasReadAndMaxSeq(
        userID: String,
        conversationIDs: List<String>,
    ): ConversationsHasReadAndMaxSeqResp = httpClient.imPostEnvelope(
        ImApiRoutes.GET_CONVERSATIONS_HAS_READ_AND_MAX_SEQ,
        ConversationsHasReadAndMaxSeqReq(userID = userID, conversationIDs = conversationIDs),
    )

    suspend fun pullMsgBySeqs(
        userID: String,
        seqRanges: List<SeqRange>,
        order: Int = 0,
    ): PullMsgResp = httpClient.imPostEnvelope(
        ImApiRoutes.PULL_BY_SEQ,
        PullMsgBySeqsReq(userID = userID, seqRanges = seqRanges, order = order),
    )

    suspend fun getFriendListPage(
        userID: String,
        pageNumber: Int,
        pageSize: Int
    ): List<FriendInfo> {
        val resp: GetFriendListResp = httpClient.imPostEnvelope(
            ImApiRoutes.GET_FRIENDS,
            UserPaginationReq(userID, Pagination(pageNumber, pageSize)),
        )
        return resp.friendsInfo.map { it.toFriendInfo(userID) }
    }

    suspend fun getFriendList(userID: String, pageSize: Int = 100): List<FriendInfo> {
        var pageNumber = 1
        val all = mutableListOf<FriendInfo>()
        while (true) {
            val batch = getFriendListPage(userID, pageNumber, pageSize)
            if (batch.isEmpty()) break
            all.addAll(batch)
            if (batch.size < pageSize) break
            pageNumber++
        }
        return all
    }

    suspend fun getJoinedGroupListPage(
        fromUserID: String,
        pageNumber: Int,
        pageSize: Int
    ): List<GroupInfo> {
        val resp: GetJoinedGroupListResp = httpClient.imPostEnvelope(
            ImApiRoutes.GET_JOINED_GROUPS,
            FromUserPaginationReq(fromUserID, Pagination(pageNumber, pageSize)),
        )
        return resp.groups
    }

    suspend fun getJoinedGroupList(fromUserID: String, pageSize: Int = 100): List<GroupInfo> {
        var pageNumber = 1
        val all = mutableListOf<GroupInfo>()
        while (true) {
            val batch = getJoinedGroupListPage(fromUserID, pageNumber, pageSize)
            if (batch.isEmpty()) break
            all.addAll(batch)
            if (batch.size < pageSize) break
            pageNumber++
        }
        return all
    }

    suspend fun getGroupsInfo(groupIDs: List<String>): List<GroupInfo> {
        if (groupIDs.isEmpty()) return emptyList()
        val resp: GetGroupsInfoResp = httpClient.imPostEnvelope(
            ImApiRoutes.GET_GROUP_INFO,
            GroupIdsReq(groupIDs),
        )
        return resp.groupInfos
    }

    suspend fun createMoment(content: String, visibleType: Int = 1): MomentInfo {
        val resp: CreateMomentResp = httpClient.chatPostEnvelope(
            ChatApiRoutes.MOMENT_CREATE,
            CreateMomentReq(content = content, visibleType = visibleType),
        )
        return resp.moment ?: throw XueHuaException(code = -1, message = "empty response data")
    }

    suspend fun submitReport(req: ReportInfo): ReportInfo =
        httpClient.chatPostEnvelope(ChatApiRoutes.REPORT_SUBMIT, req)

    suspend fun submitAppeal(req: AppealInfo): AppealInfo =
        httpClient.adminPostEnvelope(AdminApiRoutes.APPEAL_SUBMIT, req)

    suspend fun checkAppVersion(version: String? = null): ApplicationVersionInfo =
        httpClient.chatPostEnvelope(
            ChatApiRoutes.APP_VERSION,
            AppVersionReq(platform = applicationPlatformName(), version = version),
        )

    suspend fun register(req: RegisterReq): LoginResp =
        httpClient.chatPostEnvelope(ChatApiRoutes.REGISTER, req)

    suspend fun sendVerificationCode(req: SendVerifyCodeReq) {
        httpClient.chatPostVoid(ChatApiRoutes.SEND_VERIFY_CODE, req)
    }

    suspend fun resetPassword(req: ResetPasswordReq) {
        httpClient.chatPostVoid(ChatApiRoutes.RESET_PASSWORD, req)
    }

    suspend fun createMeeting(
        creatorUserID: String,
        callType: String,
        inviteeUserIDs: List<String>,
    ): CreateMeetingResult = httpClient.chatPostEnvelope(
        ChatApiRoutes.MEETING_CREATE,
        CreateMeetingReq(creatorUserID, callType, inviteeUserIDs),
    )

    suspend fun joinMeeting(roomID: String, userID: String): JoinMeetingResult =
        httpClient.chatPostEnvelope(ChatApiRoutes.MEETING_JOIN, MeetingRoomReq(roomID, userID))

    suspend fun leaveMeeting(roomID: String, userID: String) {
        httpClient.chatPostVoid(ChatApiRoutes.MEETING_LEAVE, MeetingRoomReq(roomID, userID))
    }

    suspend fun endMeeting(roomID: String, userID: String) {
        httpClient.chatPostVoid(ChatApiRoutes.MEETING_END, MeetingRoomReq(roomID, userID))
    }

    suspend fun getRtcToken(roomID: String, userID: String): RtcTokenResp =
        httpClient.chatPostEnvelope(ChatApiRoutes.RTC_GET_TOKEN, RtcTokenReq(roomID, userID))

    suspend fun searchUsers(keyword: String): List<UserInfo> {
        val resp: UsersInfoResp =
            httpClient.chatPostEnvelope(ChatApiRoutes.SEARCH_USER, SearchUserReq(keyword))
        return resp.usersInfo
    }

    suspend fun getFriendApplications(
        userID: String,
        pageNumber: Int = 1,
        pageSize: Int = 100
    ): List<FriendApplicationInfo> {
        val resp: GetFriendApplicationsResp = httpClient.imPostEnvelope(
            ImApiRoutes.GET_FRIEND_APPLICATIONS,
            UserPaginationReq(userID, Pagination(pageNumber, pageSize)),
        )
        return resp.friendRequests
    }

    suspend fun respondFriendApplication(
        toUserID: String,
        accept: Boolean,
        handleMsg: String = ""
    ) {
        httpClient.imPostVoid(
            ImApiRoutes.ACCEPT_FRIEND,
            FriendApplyResponseReq(toUserID, if (accept) 1 else -1, handleMsg),
        )
    }

    suspend fun addFriendRequest(toUserID: String, reqMsg: String = "") {
        httpClient.imPostVoid(ImApiRoutes.ADD_FRIEND, AddFriendReq(toUserID, reqMsg))
    }

    suspend fun deleteFriend(ownerUserID: String, friendUserID: String) {
        httpClient.imPostVoid(
            ImApiRoutes.DELETE_FRIEND,
            OwnerFriendReq(ownerUserID = ownerUserID, friendUserID = friendUserID),
        )
    }

    suspend fun getBlackList(
        userID: String,
        pageNumber: Int = 1,
        pageSize: Int = 100
    ): List<BlacklistInfo> {
        val resp: GetBlackListResp = httpClient.imPostEnvelope(
            ImApiRoutes.GET_BLACK_LIST,
            UserPaginationReq(userID, Pagination(pageNumber, pageSize)),
        )
        return resp.blacks
    }

    suspend fun addBlack(ownerUserID: String, blackUserID: String, ex: String = "") {
        httpClient.imPostVoid(
            ImApiRoutes.ADD_BLACK,
            OwnerBlackReq(ownerUserID = ownerUserID, blackUserID = blackUserID, ex = ex),
        )
    }

    suspend fun removeBlack(ownerUserID: String, blackUserID: String) {
        httpClient.imPostVoid(
            ImApiRoutes.REMOVE_BLACK,
            OwnerBlackReq(ownerUserID = ownerUserID, blackUserID = blackUserID),
        )
    }

    suspend fun createGroup(groupName: String, memberUserIDs: List<String>): GroupInfo =
        httpClient.imPostEnvelope(
            ImApiRoutes.CREATE_GROUP,
            CreateGroupReq(
                memberUserIDs = memberUserIDs,
                groupInfo = GroupInfo(groupID = "", groupName = groupName),
            ),
        )

    suspend fun getGroupMembers(groupID: String): List<GroupMemberInfo> {
        val pageSize = 100
        val allMembers = mutableListOf<GroupMemberInfo>()
        var pageNumber = 1
        while (true) {
            val resp: GetGroupMemberListResp = httpClient.imPostEnvelope(
                ImApiRoutes.GET_GROUP_MEMBERS,
                GetGroupMemberListReq(
                    groupID = groupID,
                    filter = 0,
                    pagination = Pagination(pageNumber, pageSize),
                ),
            )
            allMembers += resp.members
            if (resp.members.size < pageSize) break
            pageNumber++
        }
        return allMembers
    }

    suspend fun dismissGroup(groupID: String) {
        httpClient.imPostVoid(ImApiRoutes.DISMISS_GROUP, mapOf("groupID" to groupID))
    }

    suspend fun quitGroup(userID: String, groupID: String) {
        httpClient.imPostVoid(
            ImApiRoutes.QUIT_GROUP,
            mapOf("userID" to userID, "groupID" to groupID),
        )
    }

    suspend fun setGroupInfoEx(groupInfo: GroupInfo) {
        httpClient.imPostVoid(
            ImApiRoutes.SET_GROUP_INFO_EX,
            mapOf("groupInfoForSet" to groupInfo),
        )
    }

    suspend fun inviteToGroup(groupID: String, userIDs: List<String>) {
        httpClient.imPostVoid(
            ImApiRoutes.INVITE_TO_GROUP,
            mapOf("groupID" to groupID, "invitedUserIDs" to userIDs),
        )
    }

    suspend fun kickGroupMember(groupID: String, userIDs: List<String>, reason: String? = null) {
        httpClient.imPostVoid(
            ImApiRoutes.KICK_GROUP_MEMBER,
            buildMap {
                put("groupID", groupID)
                put("kickedUserIDs", userIDs)
                reason?.let { put("reason", it) }
            },
        )
    }

    suspend fun getRecvGroupApplicationList(
        fromUserID: String,
        pageNumber: Int = 1,
        pageSize: Int = 100,
    ): List<GroupApplicationInfo> {
        val resp: GetGroupApplicationListResp = httpClient.imPostEnvelope(
            ImApiRoutes.GET_RECV_GROUP_APPLICATION_LIST,
            FromUserPaginationReq(fromUserID, Pagination(pageNumber, pageSize)),
        )
        return resp.groupRequests.map { it.toGroupApplicationInfo() }
    }

    suspend fun getSendGroupApplicationList(
        fromUserID: String,
        pageNumber: Int = 1,
        pageSize: Int = 100,
    ): List<GroupApplicationInfo> {
        val resp: GetGroupApplicationListResp = httpClient.imPostEnvelope(
            ImApiRoutes.GET_SEND_GROUP_APPLICATION_LIST,
            FromUserPaginationReq(fromUserID, Pagination(pageNumber, pageSize)),
        )
        return resp.groupRequests.map { it.toGroupApplicationInfo() }
    }

    suspend fun getMomentsList(
        ownerUserID: String = "",
        pageNumber: Int = 1,
        showNumber: Int = 20,
    ): MomentListResponse {
        val resp: MomentListResp = httpClient.chatPostEnvelope(
            ChatApiRoutes.MOMENT_LIST,
            MomentListReq(
                ownerUserID = ownerUserID,
                pageNumber = pageNumber,
                showNumber = showNumber
            ),
        )
        return MomentListResponse(total = resp.total, moments = resp.moments)
    }

    suspend fun likeMoment(momentID: String, ownerUserID: String? = null): MomentLike =
        httpClient.chatPostEnvelope(
            ChatApiRoutes.MOMENT_LIKE,
            MomentLikeReq(momentID = momentID, ownerUserID = ownerUserID),
        )

    suspend fun commentMoment(
        momentID: String,
        content: String,
        ownerUserID: String? = null,
    ): MomentComment = httpClient.chatPostEnvelope(
        ChatApiRoutes.MOMENT_COMMENT,
        MomentCommentReq(momentID = momentID, content = content, ownerUserID = ownerUserID),
    )

    suspend fun deleteMoment(momentID: String) {
        httpClient.chatPostVoid(ChatApiRoutes.MOMENT_DELETE, MomentDeleteReq(momentID = momentID))
    }

    suspend fun unlikeMoment(momentID: String, ownerUserID: String? = null) {
        httpClient.chatPostVoid(
            ChatApiRoutes.MOMENT_UNLIKE,
            MomentLikeReq(momentID = momentID, ownerUserID = ownerUserID),
        )
    }

    suspend fun deleteMomentComment(commentID: String) {
        httpClient.chatPostVoid(
            ChatApiRoutes.MOMENT_COMMENT_DELETE,
            MomentCommentDeleteReq(commentID = commentID),
        )
    }

    suspend fun getFavoriteList(pageNumber: Int = 1, showNumber: Int = 20): List<FavoriteItem> {
        val resp: FavoriteListResp = httpClient.chatPostEnvelope(
            ChatApiRoutes.FAVORITE_LIST,
            FavoriteListReq(pageNumber = pageNumber, showNumber = showNumber),
        )
        return resp.favorites
    }

    suspend fun addFavorite(item: FavoriteItem): FavoriteItem =
        httpClient.chatPostEnvelope(ChatApiRoutes.FAVORITE_ADD, item)

    suspend fun deleteFavorite(favoriteID: String) {
        httpClient.chatPostVoid(ChatApiRoutes.FAVORITE_DELETE, mapOf("favoriteID" to favoriteID))
    }

    suspend fun changePassword(userID: String, currentPassword: String, newPassword: String) {
        httpClient.chatPostVoid(
            ChatApiRoutes.CHANGE_PASSWORD,
            ChangePasswordReq(
                userID = userID,
                currentPassword = md5Hex(currentPassword),
                newPassword = md5Hex(newPassword),
            ),
        )
    }

    suspend fun deleteAccount(currentPassword: String) {
        httpClient.chatPostVoid(
            ChatApiRoutes.DELETE_ACCOUNT,
            DeleteAccountReq(currentPassword = md5Hex(currentPassword)),
        )
    }

    suspend fun getPointsBalance(): Double {
        val resp: PointsBalanceResp =
            httpClient.chatPostEnvelope(ChatApiRoutes.POINTS_BALANCE, emptyMap<String, String>())
        return resp.balance
    }

    suspend fun getUserFullInfo(userIDs: List<String>): List<UserFullInfo> {
        val resp: FindUserFullInfoResp = httpClient.chatPostEnvelope(
            ChatApiRoutes.GET_USER_FULL,
            FindUserFullInfoReq(userIDs = userIDs, pagination = Pagination(1, userIDs.size)),
        )
        return resp.users
    }

    suspend fun updateChatUserInfo(req: UpdateChatUserInfoReq) {
        httpClient.chatPostVoid(ChatApiRoutes.UPDATE_USER, req)
    }

    suspend fun checkPaymentPasswordSet(): Boolean {
        val resp: PaymentPasswordCheckResp =
            httpClient.chatPostEnvelope(
                ChatApiRoutes.PAYMENT_PASSWORD_CHECK,
                emptyMap<String, String>()
            )
        return resp.isSet
    }

    suspend fun setPaymentPassword(paymentPassword: String, loginPassword: String) {
        httpClient.chatPostVoid(
            ChatApiRoutes.PAYMENT_PASSWORD_SET,
            PaymentPasswordSetReq(paymentPassword, md5Hex(loginPassword)),
        )
    }

    suspend fun changePaymentPassword(currentPaymentPassword: String, newPaymentPassword: String) {
        httpClient.chatPostVoid(
            ChatApiRoutes.PAYMENT_PASSWORD_CHANGE,
            PaymentPasswordChangeReq(currentPaymentPassword, newPaymentPassword),
        )
    }

    suspend fun verifyPaymentPassword(paymentPassword: String) {
        httpClient.chatPostVoid(
            ChatApiRoutes.PAYMENT_PASSWORD_VERIFY,
            PaymentPasswordVerifyReq(paymentPassword),
        )
    }

    suspend fun getPointsTransactions(
        pageNumber: Int = 1,
        showNumber: Int = 20,
        txType: Int? = null,
    ): Pair<Int, List<PointsTransaction>> {
        val resp: PointsTransactionsResp = httpClient.chatPostEnvelope(
            ChatApiRoutes.POINTS_TRANSACTIONS,
            PointsTransactionsReq(pageNumber, showNumber, txType),
        )
        return resp.total to resp.transactions
    }

    suspend fun setConversations(userID: String, conversationID: String, req: ConversationReq) {
        val body = buildMap {
            put("userID", userID)
            put("conversationID", conversationID)
            req.groupID?.let { put("groupID", it) }
            req.recvMsgOpt?.let { put("recvMsgOpt", it) }
            req.isPinned?.let { put("isPinned", it) }
            req.isPrivateChat?.let { put("isPrivateChat", it) }
            req.ex?.let { put("ex", it) }
            req.burnDuration?.let { put("burnDuration", it) }
            req.isMsgDestruct?.let { put("isMsgDestruct", it) }
            req.msgDestructTime?.let { put("msgDestructTime", it) }
            req.groupAtType?.let { put("groupAtType", it) }
        }
        runCatching { httpClient.imPostVoid(ImApiRoutes.SET_CONVERSATIONS, body) }
    }

    suspend fun markConversationAsRead(userID: String, conversationID: String, hasReadSeq: Long) {
        val body = mapOf(
            "userID" to userID,
            "conversationID" to conversationID,
            "hasReadSeq" to hasReadSeq,
        )
        runCatching { httpClient.imPostVoid(ImApiRoutes.MARK_CONVERSATION_AS_READ, body) }
    }

    suspend fun clearConversationMsg(userID: String, conversationIDs: List<String>) {
        val body = mapOf(
            "userID" to userID,
            "conversationIDs" to conversationIDs,
        )
        runCatching { httpClient.imPostVoid(ImApiRoutes.CLEAR_CONVERSATION_MSG, body) }
    }

    suspend fun getPartLimit(): PartLimitResp =
        httpClient.imPostEnvelope(ImApiRoutes.OBJECT_PART_LIMIT, emptyMap<String, String>())

    suspend fun initiateMultipartUpload(req: InitiateMultipartUploadReq): InitiateMultipartUploadResp =
        httpClient.imPostEnvelope(ImApiRoutes.OBJECT_INITIATE_MULTIPART_UPLOAD, req)

    suspend fun authSign(uploadID: String, partNumbers: List<Int>): AuthSignResp =
        httpClient.imPostEnvelope(
            ImApiRoutes.OBJECT_AUTH_SIGN,
            mapOf("uploadID" to uploadID, "partNumbers" to partNumbers),
        )

    suspend fun completeMultipartUpload(req: CompleteMultipartUploadReq): CompleteMultipartUploadResp =
        httpClient.imPostEnvelope(ImApiRoutes.OBJECT_COMPLETE_MULTIPART_UPLOAD, req)

    suspend fun revokeMsg(userID: String, conversationID: String, seq: Long) {
        httpClient.imPostVoid(
            ImApiRoutes.REVOKE_MSG,
            RevokeMsgReq(userID = userID, conversationID = conversationID, seq = seq),
        )
    }

    suspend fun deleteMsgs(userID: String, conversationID: String, seqs: List<Long>) {
        httpClient.imPostVoid(
            ImApiRoutes.DELETE_MSG,
            DeleteMsgsReq(userID = userID, conversationID = conversationID, seqs = seqs),
        )
    }

    suspend fun markMsgsAsRead(userID: String, conversationID: String, seqs: List<Long>) {
        httpClient.imPostVoid(
            ImApiRoutes.MARK_MSG_READ,
            MarkMsgsAsReadReq(userID = userID, conversationID = conversationID, seqs = seqs),
        )
    }

    suspend fun searchMsg(req: SearchMsgReq): SearchMsgResp =
        httpClient.imPostEnvelope(ImApiRoutes.SEARCH_MSG, req)

    suspend fun clearAllMsg(userID: String) {
        httpClient.imPostVoid(ImApiRoutes.CLEAR_ALL_MSG, ClearAllMsgReq(userID))
    }

    suspend fun joinGroup(
        groupID: String,
        reqMessage: String = "",
        joinSource: Int = 3,
        inviterUserID: String = "",
        ex: String = "",
    ) {
        httpClient.imPostVoid(
            ImApiRoutes.JOIN_GROUP,
            JoinGroupReq(
                groupID = groupID,
                reqMessage = reqMessage,
                joinSource = joinSource,
                inviterUserID = inviterUserID,
                ex = ex,
            ),
        )
    }

    suspend fun groupApplicationResponse(
        groupID: String,
        fromUserID: String,
        handledMsg: String,
        handleResult: Int,
    ) {
        httpClient.imPostVoid(
            ImApiRoutes.GROUP_APPLICATION_RESPONSE,
            GroupApplicationResponseReq(
                groupID = groupID,
                fromUserID = fromUserID,
                handledMsg = handledMsg,
                handleResult = handleResult,
            ),
        )
    }

    suspend fun getGroupApplicationUnhandledCount(userID: String, time: Long = 0): Int {
        val resp: GetGroupApplicationUnhandledCountResp = httpClient.imPostEnvelope(
            ImApiRoutes.GET_GROUP_APPLICATION_UNHANDLED_COUNT,
            GetGroupApplicationUnhandledCountReq(userID = userID, time = time),
        )
        return resp.count
    }

    suspend fun sendRedPacket(req: SendRedPacketRequest): String {
        val resp: SendRedPacketResp = httpClient.chatPostEnvelope(ChatApiRoutes.RED_PACKET_SEND, req)
        return resp.packetID
    }

    suspend fun grabRedPacket(packetID: String): Double {
        val resp: GrabRedPacketResp = httpClient.chatPostEnvelope(
            ChatApiRoutes.RED_PACKET_GRAB,
            mapOf("packetID" to packetID),
        )
        return resp.amount
    }

    suspend fun getRedPacketDetail(packetID: String): RedPacketDetail =
        httpClient.chatPostEnvelope(
            ChatApiRoutes.RED_PACKET_DETAIL,
            mapOf("packetID" to packetID),
        )

    suspend fun resetPaymentPassword(
        verifyCode: String,
        newPaymentPassword: String,
        areaCode: String? = null,
        phoneNumber: String? = null,
        email: String? = null,
    ) {
        httpClient.chatPostVoid(
            ChatApiRoutes.PAYMENT_PASSWORD_RESET,
            ResetPaymentPasswordReq(
                areaCode = areaCode,
                phoneNumber = phoneNumber,
                email = email,
                verifyCode = verifyCode,
                newPaymentPassword = newPaymentPassword,
            ),
        )
    }

    suspend fun updateFcmToken(
        account: String,
        fcmToken: String,
        expireTime: Long = 0,
    ) {
        httpClient.imPostVoid(
            ImApiRoutes.FCM_UPDATE_TOKEN,
            FcmUpdateTokenReq(
                platformID = currentPlatform().value.toString(),
                fcmToken = fcmToken,
                account = account,
                expireTime = expireTime,
            ),
        )
    }

    suspend fun forceLogout(userID: String, platformID: Int = currentPlatform().value) {
        httpClient.imPostVoid(
            ImApiRoutes.FORCE_LOGOUT,
            ForceLogoutReq(platformID = platformID, userID = userID),
        )
    }

    suspend fun getDesignatedFriends(ownerUserID: String, friendUserIDs: List<String>): List<FriendInfo> {
        if (friendUserIDs.isEmpty()) return emptyList()
        val resp: GetFriendListResp = httpClient.imPostEnvelope(
            ImApiRoutes.GET_DESIGNATED_FRIENDS,
            mapOf("ownerUserID" to ownerUserID, "friendUserIDs" to friendUserIDs),
        )
        return resp.friendsInfo.map { it.toFriendInfo(ownerUserID) }
    }

    suspend fun getRecvFriendApplications(
        userID: String,
        pageNumber: Int = 1,
        pageSize: Int = 100,
    ): List<FriendApplicationInfo> {
        val resp: GetFriendApplicationsResp = httpClient.imPostEnvelope(
            ImApiRoutes.GET_FRIEND_APPLICATIONS,
            UserPaginationReq(userID, Pagination(pageNumber, pageSize)),
        )
        return resp.friendRequests
    }

    suspend fun getSelfFriendApplications(
        userID: String,
        pageNumber: Int = 1,
        pageSize: Int = 100,
    ): List<FriendApplicationInfo> {
        val resp: GetFriendApplicationsResp = httpClient.imPostEnvelope(
            ImApiRoutes.GET_SELF_FRIEND_APPLICATIONS,
            UserPaginationReq(userID, Pagination(pageNumber, pageSize)),
        )
        return resp.friendRequests
    }

    suspend fun getSelfUnhandledApplyCount(userID: String, time: Long = 0): Int {
        val resp: GetGroupApplicationUnhandledCountResp = httpClient.imPostEnvelope(
            ImApiRoutes.GET_SELF_UNHANDLED_APPLY_COUNT,
            mapOf("userID" to userID, "time" to time),
        )
        return resp.count
    }

    suspend fun updateFriends(ownerUserID: String, friendUserIDs: List<String>, remark: String? = null) {
        val body = buildMap {
            put("ownerUserID", ownerUserID)
            put("friendUserIDs", friendUserIDs)
            remark?.let { put("remark", it) }
        }
        httpClient.imPostVoid(ImApiRoutes.UPDATE_FRIENDS, body)
    }

    suspend fun searchFriendInfo(keyword: String): List<FriendInfo> {
        val resp: GetFriendListResp = httpClient.chatPostEnvelope(
            ChatApiRoutes.SEARCH_FRIEND,
            mapOf("keyword" to keyword),
        )
        return resp.friendsInfo.map { it.toFriendInfo("") }
    }

    suspend fun searchUserFullInfo(keyword: String): List<UserFullInfo> {
        val resp: FindUserFullInfoResp = httpClient.chatPostEnvelope(
            ChatApiRoutes.SEARCH_USER,
            SearchUserReq(keyword),
        )
        return resp.users
    }

    suspend fun subscribeUsersStatus(userID: String, userIDs: List<String>, genre: Int = 1): List<UserStatusInfo> {
        val resp: UserStatusListResp = httpClient.imPostEnvelope(
            ImApiRoutes.SUBSCRIBE_USERS_STATUS,
            mapOf("userID" to userID, "userIDs" to userIDs, "genre" to genre),
        )
        return resp.statusList
    }

    suspend fun getSubscribeUsersStatus(userID: String): List<UserStatusInfo> {
        val resp: UserStatusListResp = httpClient.imPostEnvelope(
            ImApiRoutes.GET_SUBSCRIBE_USERS_STATUS,
            mapOf("userID" to userID),
        )
        return resp.statusList
    }

    suspend fun getUserStatus(userID: String, userIDs: List<String>): List<UserStatusInfo> {
        val resp: UserStatusListResp = httpClient.imPostEnvelope(
            ImApiRoutes.GET_USERS_STATUS,
            mapOf("userID" to userID, "userIDs" to userIDs),
        )
        return resp.statusList
    }

    suspend fun getUserClientConfig(userID: String): Map<String, String> {
        val resp: UserClientConfigResp = httpClient.imPostEnvelope(
            ImApiRoutes.GET_USER_CLIENT_CONFIG,
            mapOf("userID" to userID),
        )
        return resp.config
    }

    suspend fun getGroupMembersInfo(groupID: String, userIDs: List<String>): List<GroupMemberInfo> {
        if (userIDs.isEmpty()) return emptyList()
        val resp: GetGroupMemberListResp = httpClient.imPostEnvelope(
            ImApiRoutes.GET_GROUP_MEMBERS_INFO,
            mapOf("groupID" to groupID, "userIDs" to userIDs),
        )
        return resp.members
    }

    suspend fun setGroupMemberInfo(groupID: String, userID: String, nickname: String? = null, faceURL: String? = null) {
        val memberInfo = buildMap {
            put("groupID", groupID)
            put("userID", userID)
            nickname?.let { put("nickname", it) }
            faceURL?.let { put("faceURL", it) }
        }
        httpClient.imPostVoid(ImApiRoutes.SET_GROUP_MEMBER_INFO, mapOf("members" to listOf(memberInfo)))
    }

    suspend fun transferGroup(groupID: String, newOwnerUserID: String) {
        httpClient.imPostVoid(
            ImApiRoutes.TRANSFER_GROUP,
            mapOf("groupID" to groupID, "newOwnerUserID" to newOwnerUserID),
        )
    }

    suspend fun muteGroupMember(groupID: String, userID: String, mutedSeconds: Long) {
        httpClient.imPostVoid(
            ImApiRoutes.MUTE_GROUP_MEMBER,
            mapOf("groupID" to groupID, "userID" to userID, "mutedSeconds" to mutedSeconds),
        )
    }

    suspend fun cancelMuteGroupMember(groupID: String, userID: String) {
        httpClient.imPostVoid(
            ImApiRoutes.CANCEL_MUTE_GROUP_MEMBER,
            mapOf("groupID" to groupID, "userID" to userID),
        )
    }

    suspend fun muteGroup(groupID: String) {
        httpClient.imPostVoid(ImApiRoutes.MUTE_GROUP, mapOf("groupID" to groupID))
    }

    suspend fun cancelMuteGroup(groupID: String) {
        httpClient.imPostVoid(ImApiRoutes.CANCEL_MUTE_GROUP, mapOf("groupID" to groupID))
    }

    suspend fun fetchFavoriteListFromServer(pageNumber: Int = 1, showNumber: Int = 20): FavoriteListResponse {
        val resp: FavoriteListResp = httpClient.chatPostEnvelope(
            ChatApiRoutes.FAVORITE_LIST,
            FavoriteListReq(pageNumber = pageNumber, showNumber = showNumber),
        )
        return FavoriteListResponse(total = resp.total, favorites = resp.favorites)
    }

    suspend fun removeFavoriteByTarget(targetType: String, targetID: String) {
        httpClient.chatPostVoid(
            ChatApiRoutes.FAVORITE_REMOVE,
            mapOf("targetType" to targetType, "targetID" to targetID),
        )
    }

    suspend fun createReport(
        targetType: String,
        targetID: String,
        category: String,
        description: String? = null,
        messageID: String? = null,
        evidenceUrls: List<String>? = null,
    ): CreateReportResult = httpClient.chatPostEnvelope(
        ChatApiRoutes.CREATE_REPORT,
        buildMap {
            put("targetType", targetType)
            put("targetID", targetID)
            put("category", category)
            description?.let { put("description", it) }
            messageID?.let { put("messageID", it) }
            evidenceUrls?.let { put("evidenceUrls", it) }
        },
    )

    suspend fun requestAppealCaptcha(): AppealCaptcha =
        httpClient.adminPostEnvelope(AdminApiRoutes.APPEAL_CAPTCHA, emptyMap<String, String>())

    suspend fun uploadAppealEvidence(
        appealToken: String,
        bytes: ByteArray,
        fileName: String,
    ): AppealUploadResult = httpClient.adminPostEnvelope(
        AdminApiRoutes.APPEAL_UPLOAD,
        mapOf(
            "appealToken" to appealToken,
            "fileName" to fileName,
            "file" to kotlin.io.encoding.Base64.encode(bytes),
        ),
    )

    suspend fun getLatestApplicationVersion(platform: String, currentVersion: String? = null): ApplicationVersionInfo? {
        val resp: LatestAppVersionResp = httpClient.chatPostEnvelope(
            ChatApiRoutes.LATEST_APPLICATION_VERSION,
            mapOf("platform" to platform, "version" to currentVersion),
        )
        return resp.version
    }
}
