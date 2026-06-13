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


class FavoriteManager internal constructor(
    private val apiService: ImApiService,
    private val databaseService: DatabaseService,
    private val eventEmitter: SdkEventEmitter,
) {
    suspend fun getFavoriteList(): List<FavoriteItem> = withContext(ioDispatcher) {
        val local = databaseService.getFavoritesPage(0, 1000)
        if (local.isNotEmpty()) return@withContext local
        FavoriteSync.syncFromServer(apiService, databaseService, eventEmitter)
    }

    suspend fun addFavorite(item: FavoriteItem) = withContext(ioDispatcher) {
        val saved = apiService.addFavorite(item)
        databaseService.batchUpsertFavorites(listOf(saved))
        eventEmitter.emitFavorite(com.kurban.xuehuaim.sdk.event.FavoriteEvent.Added(saved))
        saved
    }

    suspend fun deleteFavorite(favoriteId: String) = withContext(ioDispatcher) {
        apiService.deleteFavorite(favoriteId)
        databaseService.deleteFavorite(favoriteId)
        eventEmitter.emitFavorite(com.kurban.xuehuaim.sdk.event.FavoriteEvent.Deleted(favoriteId))
    }

    suspend fun fetchFavoriteListFromServer(pageNumber: Int = 1, showNumber: Int = 20) =
        fetchFavoriteListFromServer(apiService, pageNumber, showNumber)

    suspend fun syncFromServer(pageNumber: Int = 1, showNumber: Int = 100): List<FavoriteItem> =
        FavoriteSync.syncFromServer(apiService, databaseService, eventEmitter, pageNumber, showNumber)

    suspend fun isFavorited(targetType: String, targetId: String): Boolean =
        isFavorited(apiService, targetType, targetId)

    suspend fun isMessageFavorited(clientMsgId: String): Boolean =
        isMessageFavorited(apiService, clientMsgId)

    suspend fun isMomentFavorited(momentId: String): Boolean =
        isMomentFavorited(apiService, momentId)

    suspend fun addMessage(message: Message) =
        addMessage(apiService, eventEmitter, message)

    suspend fun removeMessage(clientMsgId: String) =
        removeMessage(apiService, eventEmitter, clientMsgId)

    suspend fun addMoment(moment: MomentInfo) =
        addMoment(apiService, eventEmitter, moment)

    suspend fun removeMoment(momentId: String) =
        removeMoment(apiService, eventEmitter, momentId)

    suspend fun addMomentComment(comment: MomentCommentWithUser): FavoriteItem? =
        addMomentComment(apiService, eventEmitter, comment)

    suspend fun removeMomentComment(commentID: String): Boolean =
        removeMomentComment(apiService, eventEmitter, commentID)

    suspend fun addNote(title: String, content: String) =
        addNote(apiService, eventEmitter, title, content)

    suspend fun updateNote(favoriteId: String, data: String) =
        updateNote(apiService, eventEmitter, favoriteId, data)

    suspend fun addLink(linkId: String, data: String) =
        addLink(apiService, eventEmitter, linkId, data)

    suspend fun updateFavorite(favoriteId: String, data: String) =
        updateFavorite(apiService, eventEmitter, favoriteId, data)

    suspend fun removeFavoriteItem(targetType: String, targetId: String) =
        removeFavoriteItem(apiService, eventEmitter, targetType, targetId)
}
