package com.kurban.xuehuaim.sdk.manager

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.model.FavoriteItem
import com.kurban.xuehuaim.sdk.model.Message
import com.kurban.xuehuaim.sdk.model.MomentCommentWithUser
import com.kurban.xuehuaim.sdk.model.MomentInfo
import com.kurban.xuehuaim.sdk.network.http.ImApiService
import com.kurban.xuehuaim.sdk.platform.ioDispatcher
import com.kurban.xuehuaim.sdk.sync.FavoriteSync
import kotlinx.coroutines.withContext


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
        FavoriteSync.syncFromServer(
            apiService,
            databaseService,
            eventEmitter,
            pageNumber,
            showNumber
        )

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
