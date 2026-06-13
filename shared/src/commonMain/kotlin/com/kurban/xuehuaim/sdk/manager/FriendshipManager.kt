package com.kurban.xuehuaim.sdk.manager

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.model.BlacklistInfo
import com.kurban.xuehuaim.sdk.model.FriendApplicationInfo
import com.kurban.xuehuaim.sdk.model.FriendInfo
import com.kurban.xuehuaim.sdk.model.UserInfo
import com.kurban.xuehuaim.sdk.network.http.ImApiService
import com.kurban.xuehuaim.sdk.network.http.LoginUserIdProvider
import com.kurban.xuehuaim.sdk.platform.ioDispatcher
import com.kurban.xuehuaim.sdk.sync.FriendSync
import com.kurban.xuehuaim.sdk.sync.VersionedListPager
import kotlinx.coroutines.withContext


class FriendshipManager internal constructor(
    private val apiService: ImApiService,
    private val databaseService: DatabaseService,
    private val eventEmitter: SdkEventEmitter,
    private val loginUserId: LoginUserIdProvider,
) {
    suspend fun getFriendList(filterBlack: Boolean = false): List<FriendInfo> =
        withContext(ioDispatcher) {
            var list = databaseService.getAllFriends()
            if (filterBlack) {
                val blackIds = databaseService.getBlackUserIds()
                list = list.filter { it.userID !in blackIds }
            }
            list
        }

    suspend fun getFriendListPage(
        filterBlack: Boolean = false,
        offset: Int = 0,
        count: Int = 40,
    ): List<FriendInfo> = withContext(ioDispatcher) {
        VersionedListPager.fetchFriendsPage(
            databaseService = databaseService,
            apiService = apiService,
            userId = loginUserId.requireUserId(),
            offset = offset,
            count = count,
            filterBlack = filterBlack,
        )
    }

    suspend fun addFriend(userId: String, reqMsg: String = "") = withContext(ioDispatcher) {
        apiService.addFriendRequest(userId, reqMsg)
    }

    suspend fun deleteFriend(userId: String) = withContext(ioDispatcher) {
        apiService.deleteFriend(loginUserId.requireUserId(), userId)
        databaseService.deleteFriend(userId)
        eventEmitter.emitFriendship(
            com.kurban.xuehuaim.sdk.event.FriendshipEvent.FriendDeleted(userId),
        )
    }

    suspend fun getFriendApplications(): List<FriendApplicationInfo> =
        apiService.getFriendApplications(loginUserId.requireUserId())

    suspend fun acceptFriendApplication(toUserID: String, handleMsg: String = "") =
        respondFriendApplication(toUserID, accept = true, handleMsg)

    suspend fun refuseFriendApplication(toUserID: String, handleMsg: String = "") =
        respondFriendApplication(toUserID, accept = false, handleMsg)

    private suspend fun respondFriendApplication(
        toUserID: String,
        accept: Boolean,
        handleMsg: String,
    ) = withContext(ioDispatcher) {
        apiService.respondFriendApplication(toUserID, accept, handleMsg)
        if (accept) {
            FriendSync.syncFriends(
                apiService,
                databaseService,
                eventEmitter,
                loginUserId.requireUserId(),
            )
        }
    }

    suspend fun getBlacklist(): List<BlacklistInfo> = withContext(ioDispatcher) {
        val userId = loginUserId.requireUserId()
        var list = databaseService.getBlackList()
        if (list.isEmpty()) {
            FriendSync.syncBlackList(apiService, databaseService, userId)
            list = databaseService.getBlackList()
        }
        if (list.isEmpty()) {
            list = apiService.getBlackList(userId)
            list.forEach { databaseService.insertOrReplaceBlack(it) }
        }
        list
    }

    suspend fun addBlacklist(userID: String, ex: String? = null) = withContext(ioDispatcher) {
        apiService.addBlack(loginUserId.requireUserId(), userID)
        FriendSync.syncBlackList(apiService, databaseService, loginUserId.requireUserId())
    }

    suspend fun removeBlacklist(userID: String) = withContext(ioDispatcher) {
        apiService.removeBlack(loginUserId.requireUserId(), userID)
        databaseService.deleteBlack(userID)
    }

    suspend fun searchUsers(keyword: String): List<UserInfo> = apiService.searchUsers(keyword)

    suspend fun getFriendsInfo(userIds: List<String>): List<FriendInfo> =
        getFriendsInfo(apiService, loginUserId, userIds)

    suspend fun checkFriend(userId: String): Boolean =
        checkFriend(apiService, loginUserId, userId)

    suspend fun searchFriends(keyword: String): List<FriendInfo> =
        searchFriends(apiService, keyword)

    suspend fun updateFriends(userIds: List<String>, remark: String? = null) =
        updateFriends(apiService, loginUserId, userIds, remark)

    suspend fun getFriendApplicationListAsRecipient(
        pageNumber: Int = 1,
        pageSize: Int = 100,
    ) = getFriendApplicationListAsRecipient(apiService, loginUserId, pageNumber, pageSize)

    suspend fun getFriendApplicationListAsApplicant(
        pageNumber: Int = 1,
        pageSize: Int = 100,
    ) = getFriendApplicationListAsApplicant(apiService, loginUserId, pageNumber, pageSize)

    suspend fun getFriendApplicationUnhandledCount(time: Long = 0): Int =
        getFriendApplicationUnhandledCount(apiService, loginUserId, time)
}
