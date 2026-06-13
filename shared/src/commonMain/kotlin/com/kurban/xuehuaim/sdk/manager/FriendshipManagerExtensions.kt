package com.kurban.xuehuaim.sdk.manager

import com.kurban.xuehuaim.sdk.model.FriendInfo
import com.kurban.xuehuaim.sdk.network.http.ImApiService
import com.kurban.xuehuaim.sdk.network.http.LoginUserIdProvider
import com.kurban.xuehuaim.sdk.platform.ioDispatcher
import kotlinx.coroutines.withContext

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

internal suspend fun FriendshipManager.searchFriends(
    apiService: ImApiService,
    keyword: String
): List<FriendInfo> =
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
