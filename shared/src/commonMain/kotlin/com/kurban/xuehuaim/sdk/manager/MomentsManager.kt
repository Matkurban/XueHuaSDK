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


class MomentsManager internal constructor(
    private val apiService: ImApiService,
    private val databaseService: DatabaseService,
    private val eventEmitter: SdkEventEmitter,
    private val loginUserId: LoginUserIdProvider,
    private val scope: CoroutineScope = sdkScope,
) {
    suspend fun createMoment(content: String): MomentInfo {
        val moment = apiService.createMoment(content = content)
        eventEmitter.emitMoments(com.kurban.xuehuaim.sdk.event.MomentsEvent.NewMoment(moment))
        return moment
    }

    suspend fun deleteMoment(momentId: String) = withContext(ioDispatcher) {
        apiService.deleteMoment(momentId)
        databaseService.deleteMoment(momentId)
        eventEmitter.emitMoments(com.kurban.xuehuaim.sdk.event.MomentsEvent.MomentDeleted(momentId))
    }

    suspend fun getMomentList(
        ownerUserID: String? = null,
        pageNumber: Int = 1,
        showNumber: Int = 20,
    ): MomentListResponse = MomentSync.getMomentList(
        apiService = apiService,
        databaseService = databaseService,
        eventEmitter = eventEmitter,
        scope = scope,
        ownerUserID = ownerUserID,
        pageNumber = pageNumber,
        showNumber = showNumber,
    )

    suspend fun likeMoment(momentId: String) = withContext(ioDispatcher) {
        val like = apiService.likeMoment(momentId, ownerUserID = loginUserId())
        eventEmitter.emitMoments(com.kurban.xuehuaim.sdk.event.MomentsEvent.Liked(momentId, like))
    }

    suspend fun commentMoment(momentId: String, content: String) = withContext(ioDispatcher) {
        val comment = apiService.commentMoment(momentId, content, ownerUserID = loginUserId())
        eventEmitter.emitMoments(com.kurban.xuehuaim.sdk.event.MomentsEvent.Commented(momentId, comment))
    }

    suspend fun unlikeMoment(momentId: String, ownerUserID: String? = null) =
        withContext(ioDispatcher) {
            apiService.unlikeMoment(momentId, ownerUserID = ownerUserID ?: loginUserId())
            eventEmitter.emitMoments(
                com.kurban.xuehuaim.sdk.event.MomentsEvent.Unliked(
                    momentId,
                    loginUserId().orEmpty()
                ),
            )
        }

    suspend fun deleteComment(momentId: String, commentId: String) = withContext(ioDispatcher) {
        apiService.deleteMomentComment(commentId)
        eventEmitter.emitMoments(
            com.kurban.xuehuaim.sdk.event.MomentsEvent.CommentDeleted(momentId, commentId),
        )
    }

    suspend fun fetchMomentListFromServer(
        ownerUserID: String? = null,
        pageNumber: Int = 1,
        showNumber: Int = 20,
    ): MomentListResponse = MomentSync.fetchMomentListFromServer(
        apiService = apiService,
        databaseService = databaseService,
        eventEmitter = eventEmitter,
        ownerUserID = ownerUserID,
        pageNumber = pageNumber,
        showNumber = showNumber,
    )

    suspend fun syncFromServer(pageNumber: Int = 1, showNumber: Int = 20): List<MomentInfo> =
        MomentSync.syncFromServer(
            apiService,
            databaseService,
            eventEmitter,
            pageNumber,
            showNumber,
        )

    suspend fun handleNotification(key: String, data: Map<String, String>) =
        handleNotification(eventEmitter, key, data)
}
