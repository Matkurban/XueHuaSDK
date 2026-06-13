package com.kurban.xuehuaim.sdk.manager

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.model.MomentInfo
import com.kurban.xuehuaim.sdk.model.MomentListResponse
import com.kurban.xuehuaim.sdk.network.http.ImApiService
import com.kurban.xuehuaim.sdk.network.http.LoginUserIdProvider
import com.kurban.xuehuaim.sdk.platform.ioDispatcher
import com.kurban.xuehuaim.sdk.platform.sdkScope
import com.kurban.xuehuaim.sdk.sync.MomentSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext


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
        eventEmitter.emitMoments(
            com.kurban.xuehuaim.sdk.event.MomentsEvent.Commented(
                momentId,
                comment
            )
        )
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
