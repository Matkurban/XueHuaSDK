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
