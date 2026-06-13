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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

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
