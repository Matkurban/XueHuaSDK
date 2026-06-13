package com.kurban.xuehuaim.sdk.manager

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.db.SendingMessage
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
import kotlin.time.Duration.Companion.milliseconds


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
