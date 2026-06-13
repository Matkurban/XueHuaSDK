package com.kurban.xuehuaim.sdk.manager

import com.kurban.xuehuaim.sdk.event.MomentsEvent
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.model.MomentComment
import com.kurban.xuehuaim.sdk.model.MomentInfo
import com.kurban.xuehuaim.sdk.model.MomentLike
import com.kurban.xuehuaim.sdk.platform.ioDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

internal suspend fun MomentsManager.handleNotification(
    eventEmitter: SdkEventEmitter,
    key: String,
    data: Map<String, String>,
) = withContext(ioDispatcher) {
    when (key) {
        "moment_created" -> data["moment"]?.let {
            runCatching {
                Json.decodeFromString<MomentInfo>(it)
            }.getOrNull()?.let { moment ->
                eventEmitter.emitMoments(MomentsEvent.NewMoment(moment))
            }
        }

        "moment_liked" -> {
            val momentId = data["momentID"] ?: return@withContext
            val like = MomentLike(
                userID = data["userID"].orEmpty(),
                nickname = data["nickname"],
                createTime = data["createTime"],
            )
            eventEmitter.emitMoments(MomentsEvent.Liked(momentId, like))
        }

        "moment_commented" -> {
            val momentId = data["momentID"] ?: return@withContext
            val comment = MomentComment(
                commentID = data["commentID"].orEmpty(),
                userID = data["userID"].orEmpty(),
                content = data["content"],
                createTime = data["createTime"],
            )
            eventEmitter.emitMoments(MomentsEvent.Commented(momentId, comment))
        }
    }
}
