package com.kurban.xuehuaim.sdk.manager

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.model.AuthCacheData
import com.kurban.xuehuaim.sdk.model.FriendInfo
import com.kurban.xuehuaim.sdk.model.UserFullInfo
import com.kurban.xuehuaim.sdk.model.UserInfo
import com.kurban.xuehuaim.sdk.model.UserStatusInfo
import com.kurban.xuehuaim.sdk.network.http.ImApiService
import com.kurban.xuehuaim.sdk.network.http.LoginUserIdProvider
import com.kurban.xuehuaim.sdk.platform.ioDispatcher
import com.kurban.xuehuaim.sdk.util.md5Hex
import kotlinx.coroutines.withContext


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
