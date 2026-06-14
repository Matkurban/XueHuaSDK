package com.kurban.xuehuaim.sdk.manager

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.model.MomentCommentWithUser
import com.kurban.xuehuaim.sdk.model.MomentCreateReq
import com.kurban.xuehuaim.sdk.model.MomentInfo
import com.kurban.xuehuaim.sdk.model.MomentLikeWithUser
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
    suspend fun createMoment(request: MomentCreateReq): MomentInfo = withContext(ioDispatcher) {
        val moment = apiService.createMoment(request)
        databaseService.insertOrReplaceMoment(moment)
        eventEmitter.emitMoments(com.kurban.xuehuaim.sdk.event.MomentsEvent.NewMoment(moment))
        moment
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

    suspend fun likeMoment(momentId: String, ownerUserID: String? = null) = withContext(ioDispatcher) {
        val like = apiService.likeMoment(momentId, ownerUserID = ownerUserID ?: loginUserId())
        updateLocalMomentLike(momentId, like, add = true)
        eventEmitter.emitMoments(com.kurban.xuehuaim.sdk.event.MomentsEvent.Liked(momentId, like))
    }

    suspend fun commentMoment(
        momentId: String,
        content: String,
        replyToUserID: String? = null,
        ownerUserID: String? = null,
    ): MomentCommentWithUser = withContext(ioDispatcher) {
        val comment = apiService.commentMoment(
            momentID = momentId,
            content = content,
            replyToUserID = replyToUserID,
            ownerUserID = ownerUserID ?: loginUserId(),
        )
        updateLocalMomentComment(momentId, comment, add = true)
        eventEmitter.emitMoments(
            com.kurban.xuehuaim.sdk.event.MomentsEvent.Commented(momentId, comment),
        )
        comment
    }

    suspend fun unlikeMoment(momentId: String, ownerUserID: String? = null) =
        withContext(ioDispatcher) {
            val userId = ownerUserID ?: loginUserId().orEmpty()
            apiService.unlikeMoment(momentId, ownerUserID = ownerUserID ?: loginUserId())
            updateLocalMomentLike(
                momentId,
                MomentLikeWithUser(momentID = momentId, userID = userId),
                add = false,
            )
            eventEmitter.emitMoments(
                com.kurban.xuehuaim.sdk.event.MomentsEvent.Unliked(momentId, userId),
            )
        }

    suspend fun deleteComment(momentId: String, commentId: String) = withContext(ioDispatcher) {
        apiService.deleteMomentComment(commentId)
        updateLocalMomentComment(momentId, commentId = commentId, add = false)
        eventEmitter.emitMoments(
            com.kurban.xuehuaim.sdk.event.MomentsEvent.CommentDeleted(momentId, commentId),
        )
    }

    suspend fun getMomentById(momentId: String): MomentInfo? = withContext(ioDispatcher) {
        databaseService.getMomentById(momentId)
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
        handleNotification(eventEmitter, databaseService, key, data)

    private suspend fun updateLocalMomentLike(
        momentId: String,
        like: MomentLikeWithUser,
        add: Boolean,
    ) {
        val moment = databaseService.getMomentById(momentId) ?: return
        val likes = moment.likes.toMutableList()
        if (add) {
            likes.removeAll { it.userID == like.userID }
            likes.add(like)
        } else {
            likes.removeAll { it.userID == like.userID }
        }
        val updated = moment.copy(likes = likes, likeCount = likes.size)
        databaseService.insertOrReplaceMoment(updated)
    }

    private suspend fun updateLocalMomentComment(
        momentId: String,
        comment: MomentCommentWithUser? = null,
        commentId: String? = null,
        add: Boolean,
    ) {
        val moment = databaseService.getMomentById(momentId) ?: return
        val comments = moment.comments.toMutableList()
        if (add && comment != null) {
            if (comments.none { it.commentID == comment.commentID }) {
                comments.add(comment)
            }
        } else if (!add && commentId != null) {
            comments.removeAll { it.commentID == commentId }
        }
        val updated = moment.copy(comments = comments, commentCount = comments.size)
        databaseService.insertOrReplaceMoment(updated)
    }
}
