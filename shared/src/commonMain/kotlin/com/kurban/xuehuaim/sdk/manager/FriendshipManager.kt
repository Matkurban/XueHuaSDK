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


class FriendshipManager internal constructor(
    private val apiService: ImApiService,
    private val databaseService: DatabaseService,
    private val eventEmitter: SdkEventEmitter,
    private val loginUserId: LoginUserIdProvider,
) {
    suspend fun getFriendList(filterBlack: Boolean = false): List<FriendInfo> =
        withContext(ioDispatcher) {
            val userId = loginUserId.requireUserId()
            var list = databaseService.getAllFriends()
            if (list.isEmpty()) {
                FriendSync.syncFriends(apiService, databaseService, eventEmitter, userId)
                list = databaseService.getAllFriends()
            }
            if (list.isEmpty()) {
                list = apiService.getFriendList(userId)
                databaseService.batchUpsertFriends(list)
            }
            if (filterBlack) {
                val blackIds = databaseService.getBlackUserIds()
                list.filter { it.userID !in blackIds }
            } else {
                list
            }
        }

    suspend fun getFriendListPage(
        filterBlack: Boolean = false,
        offset: Int = 0,
        count: Int = 40,
    ): List<FriendInfo> = databaseService.getFriendsPage(offset, count, filterBlack)

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
        FriendSync.syncFriends(apiService, databaseService, eventEmitter, loginUserId.requireUserId())
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
