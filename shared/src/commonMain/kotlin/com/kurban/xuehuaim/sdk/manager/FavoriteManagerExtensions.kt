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

internal suspend fun FavoriteManager.fetchFavoriteListFromServer(
    apiService: ImApiService,
    pageNumber: Int = 1,
    showNumber: Int = 20,
): FavoriteListResponse = withContext(ioDispatcher) {
    apiService.fetchFavoriteListFromServer(pageNumber, showNumber)
}

internal suspend fun FavoriteManager.syncFromServer(
    apiService: ImApiService,
    pageNumber: Int = 1,
    showNumber: Int = 100,
): List<FavoriteItem> = withContext(ioDispatcher) {
    apiService.fetchFavoriteListFromServer(pageNumber, showNumber).favorites
}

internal suspend fun FavoriteManager.isFavorited(
    apiService: ImApiService,
    targetType: String,
    targetId: String,
): Boolean = withContext(ioDispatcher) {
    apiService.fetchFavoriteListFromServer(1, 200).favorites
        .any { it.targetType == targetType && it.targetID == targetId }
}

internal suspend fun FavoriteManager.isMessageFavorited(apiService: ImApiService, clientMsgId: String): Boolean =
    isFavorited(apiService, "message", clientMsgId)

internal suspend fun FavoriteManager.isMomentFavorited(apiService: ImApiService, momentId: String): Boolean =
    isFavorited(apiService, FavoriteType.MOMENT_CONTENT.value, momentId)

internal suspend fun FavoriteManager.addMessage(
    apiService: ImApiService,
    eventEmitter: SdkEventEmitter,
    message: Message,
): FavoriteItem = withContext(ioDispatcher) {
    val clientMsgId = message.clientMsgID
    addFavoriteItem(
        apiService,
        eventEmitter,
        FavoriteType.MESSAGE.value,
        clientMsgId,
        Json.encodeToString(message),
    )
}

internal suspend fun FavoriteManager.removeMessage(apiService: ImApiService, eventEmitter: SdkEventEmitter, clientMsgId: String) =
    removeFavoriteItem(apiService, eventEmitter, FavoriteType.MESSAGE.value, clientMsgId)

internal suspend fun FavoriteManager.addMoment(
    apiService: ImApiService,
    eventEmitter: SdkEventEmitter,
    moment: MomentInfo,
) = addFavoriteItem(
    apiService,
    eventEmitter,
    FavoriteType.MOMENT_CONTENT.value,
    moment.momentID,
    Json.encodeToString(moment),
)

internal suspend fun FavoriteManager.removeMoment(apiService: ImApiService, eventEmitter: SdkEventEmitter, momentId: String) =
    removeFavoriteItem(apiService, eventEmitter, FavoriteType.MOMENT_CONTENT.value, momentId)

internal suspend fun FavoriteManager.addMomentComment(
    apiService: ImApiService,
    eventEmitter: SdkEventEmitter,
    comment: MomentCommentWithUser,
): FavoriteItem? = addFavoriteItem(
    apiService,
    eventEmitter,
    FavoriteType.MOMENT_COMMENT.value,
    comment.commentID,
    kotlinx.serialization.json.Json.encodeToString(comment),
)

internal suspend fun FavoriteManager.removeMomentComment(
    apiService: ImApiService,
    eventEmitter: SdkEventEmitter,
    commentID: String,
): Boolean = withContext(ioDispatcher) {
    removeFavoriteItem(apiService, eventEmitter, FavoriteType.MOMENT_COMMENT.value, commentID)
    true
}

internal suspend fun FavoriteManager.addNote(
    apiService: ImApiService,
    eventEmitter: SdkEventEmitter,
    title: String,
    content: String,
): FavoriteItem? = withContext(ioDispatcher) {
    val noteId = "note_${com.kurban.xuehuaim.sdk.util.System.currentTimeMillis()}"
    val data = kotlinx.serialization.json.Json.encodeToString(
        mapOf(
            "noteID" to noteId,
            "summary" to title,
            "content" to content,
            "createdAt" to com.kurban.xuehuaim.sdk.util.System.currentTimeMillis().toString(),
        ),
    )
    addFavoriteItem(apiService, eventEmitter, FavoriteType.NOTE.value, noteId, data)
}

internal suspend fun FavoriteManager.updateNote(
    apiService: ImApiService,
    eventEmitter: SdkEventEmitter,
    favoriteId: String,
    data: String,
) = updateFavorite(apiService, eventEmitter, favoriteId, data)

internal suspend fun FavoriteManager.addLink(
    apiService: ImApiService,
    eventEmitter: SdkEventEmitter,
    linkId: String,
    data: String,
) = addFavoriteItem(apiService, eventEmitter, "link", linkId, data)

internal suspend fun FavoriteManager.updateFavorite(
    apiService: ImApiService,
    eventEmitter: SdkEventEmitter,
    favoriteId: String,
    data: String,
) = withContext(ioDispatcher) {
    val updated = FavoriteItem(
        favoriteID = favoriteId,
        userID = "",
        targetType = "note",
        targetID = favoriteId,
        data = data,
    )
    val saved = apiService.addFavorite(updated)
    eventEmitter.emitFavorite(FavoriteEvent.Updated(saved))
    saved
}

internal suspend fun FavoriteManager.removeFavoriteItem(
    apiService: ImApiService,
    eventEmitter: SdkEventEmitter,
    targetType: String,
    targetId: String,
) = withContext(ioDispatcher) {
    apiService.removeFavoriteByTarget(targetType, targetId)
    eventEmitter.emitFavorite(FavoriteEvent.Deleted(targetId))
}

private suspend fun FavoriteManager.addFavoriteItem(
    apiService: ImApiService,
    eventEmitter: SdkEventEmitter,
    targetType: String,
    targetId: String,
    data: String?,
): FavoriteItem = withContext(ioDispatcher) {
    val item = FavoriteItem(
        favoriteID = "",
        userID = "",
        targetType = targetType,
        targetID = targetId,
        data = data,
    )
    addFavorite(item)
}
