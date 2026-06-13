package com.kurban.xuehuaim.sdk.sync

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.event.FavoriteEvent
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.model.FavoriteItem
import com.kurban.xuehuaim.sdk.network.http.ImApiService
import com.kurban.xuehuaim.sdk.util.SdkLogger

internal object FavoriteSync {
    private val log = SdkLogger.tag("FavoriteSync")

    suspend fun syncFromServer(
        apiService: ImApiService,
        databaseService: DatabaseService,
        eventEmitter: SdkEventEmitter,
        pageNumber: Int = 1,
        showNumber: Int = 100,
    ): List<FavoriteItem> = runCatching {
        val favorites = apiService.getFavoriteList(pageNumber, showNumber)
        if (favorites.isNotEmpty()) {
            databaseService.batchUpsertFavorites(favorites)
            favorites.forEach { eventEmitter.emitFavorite(FavoriteEvent.Updated(it)) }
        }
        favorites
    }.getOrElse { error ->
        log.error(error) { "favorite sync failed" }
        emptyList()
    }
}
