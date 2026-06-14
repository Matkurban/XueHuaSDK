package com.kurban.xuehuaim.sdk.manager

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.event.MomentsEvent
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.model.MomentCommentWithUser
import com.kurban.xuehuaim.sdk.model.MomentInfo
import com.kurban.xuehuaim.sdk.model.MomentLikeWithUser
import com.kurban.xuehuaim.sdk.platform.ioDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

internal suspend fun MomentsManager.handleNotification(
    eventEmitter: SdkEventEmitter,
    databaseService: DatabaseService,
    key: String,
    data: Map<String, String>,
) = withContext(ioDispatcher) {
    when (key) {
        "moment_created" -> data["moment"]?.let {
            runCatching {
                Json { ignoreUnknownKeys = true }.decodeFromString<MomentInfo>(it)
            }.getOrNull()?.let { moment ->
                databaseService.insertOrReplaceMoment(moment)
                eventEmitter.emitMoments(MomentsEvent.NewMoment(moment))
            }
        }

        "moment_deleted" -> {
            val momentId = data["momentID"].orEmpty()
            if (momentId.isNotEmpty()) {
                databaseService.deleteMoment(momentId)
                eventEmitter.emitMoments(MomentsEvent.MomentDeleted(momentId))
            }
        }

        "moment_liked" -> {
            val momentId = data["momentID"].orEmpty()
            if (momentId.isEmpty()) return@withContext
            data["like"]?.let {
                runCatching {
                    Json { ignoreUnknownKeys = true }.decodeFromString<MomentLikeWithUser>(it)
                }.getOrNull()
            }?.let { like ->
                databaseService.getMomentById(momentId)?.let { moment ->
                    val likes = moment.likes.toMutableList().apply {
                        removeAll { it.userID == like.userID }
                        add(like)
                    }
                    databaseService.insertOrReplaceMoment(moment.copy(likes = likes, likeCount = likes.size))
                }
                eventEmitter.emitMoments(MomentsEvent.Liked(momentId, like))
            }
        }

        "moment_unliked" -> {
            val momentId = data["momentID"].orEmpty()
            val userId = data["userID"].orEmpty()
            if (momentId.isEmpty() || userId.isEmpty()) return@withContext
            databaseService.getMomentById(momentId)?.let { moment ->
                val likes = moment.likes.filterNot { it.userID == userId }
                databaseService.insertOrReplaceMoment(moment.copy(likes = likes, likeCount = likes.size))
            }
            eventEmitter.emitMoments(MomentsEvent.Unliked(momentId, userId))
        }

        "moment_commented" -> {
            val momentId = data["momentID"].orEmpty()
            if (momentId.isEmpty()) return@withContext
            data["comment"]?.let {
                runCatching {
                    Json { ignoreUnknownKeys = true }.decodeFromString<MomentCommentWithUser>(it)
                }.getOrNull()
            }?.let { comment ->
                databaseService.getMomentById(momentId)?.let { moment ->
                    val comments = moment.comments.toMutableList().apply {
                        if (none { it.commentID == comment.commentID }) add(comment)
                    }
                    databaseService.insertOrReplaceMoment(
                        moment.copy(comments = comments, commentCount = comments.size),
                    )
                }
                eventEmitter.emitMoments(MomentsEvent.Commented(momentId, comment))
            }
        }

        "moment_comment_deleted" -> {
            val momentId = data["momentID"].orEmpty()
            val commentId = data["commentID"].orEmpty()
            if (momentId.isEmpty() || commentId.isEmpty()) return@withContext
            databaseService.getMomentById(momentId)?.let { moment ->
                val comments = moment.comments.filterNot { it.commentID == commentId }
                databaseService.insertOrReplaceMoment(
                    moment.copy(comments = comments, commentCount = comments.size),
                )
            }
            eventEmitter.emitMoments(MomentsEvent.CommentDeleted(momentId, commentId))
        }
    }
}
