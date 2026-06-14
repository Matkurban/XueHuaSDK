package com.kurban.xuehuaim.sdk.sync

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.event.MomentsEvent
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.model.MomentListResponse
import com.kurban.xuehuaim.sdk.network.http.ImApiService
import com.kurban.xuehuaim.sdk.platform.ioDispatcher
import com.kurban.xuehuaim.sdk.util.SdkLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object MomentSync {
    private val log = SdkLogger.tag("MomentSync")
    private val inFlightMutex = Mutex()
    private val inFlightFetches = mutableSetOf<String>()

    suspend fun getMomentList(
        apiService: ImApiService,
        databaseService: DatabaseService,
        eventEmitter: SdkEventEmitter,
        scope: CoroutineScope,
        ownerUserID: String? = null,
        pageNumber: Int = 1,
        showNumber: Int = 20,
    ): MomentListResponse {
        val offset = (pageNumber - 1) * showNumber
        val local = if (!ownerUserID.isNullOrBlank()) {
            databaseService.getMomentsByUserIdPage(ownerUserID, offset, showNumber)
        } else {
            databaseService.getMomentsPage(offset, showNumber)
        }
        if (local.isNotEmpty()) {
            scope.launch(ioDispatcher) {
                fetchAndCacheMoments(
                    apiService,
                    databaseService,
                    eventEmitter,
                    ownerUserID,
                    pageNumber,
                    showNumber,
                )
            }
            return MomentListResponse(total = local.size, moments = local)
        }
        return fetchMomentListFromServer(
            apiService,
            databaseService,
            eventEmitter,
            ownerUserID,
            pageNumber,
            showNumber,
        )
    }

    suspend fun fetchMomentListFromServer(
        apiService: ImApiService,
        databaseService: DatabaseService,
        eventEmitter: SdkEventEmitter,
        ownerUserID: String? = null,
        pageNumber: Int = 1,
        showNumber: Int = 20,
    ): MomentListResponse = runCatching {
        val response = apiService.getMomentsList(
            ownerUserID = ownerUserID.orEmpty(),
            pageNumber = pageNumber,
            showNumber = showNumber,
        )
        if (response.moments.isNotEmpty()) {
            databaseService.batchUpsertMoments(response.moments)
        }
        response
    }.getOrElse { error ->
        log.error(error) { "fetchMomentListFromServer failed" }
        MomentListResponse.empty()
    }

    private suspend fun fetchAndCacheMoments(
        apiService: ImApiService,
        databaseService: DatabaseService,
        eventEmitter: SdkEventEmitter,
        ownerUserID: String?,
        pageNumber: Int,
        showNumber: Int,
    ) {
        val key = "${ownerUserID.orEmpty()}:$pageNumber:$showNumber"
        val shouldFetch = inFlightMutex.withLock {
            if (key in inFlightFetches) {
                false
            } else {
                inFlightFetches.add(key)
                true
            }
        }
        if (!shouldFetch) return

        try {
            runCatching {
                val response = apiService.getMomentsList(
                    ownerUserID = ownerUserID.orEmpty(),
                    pageNumber = pageNumber,
                    showNumber = showNumber,
                )
                if (response.moments.isNotEmpty()) {
                    databaseService.batchUpsertMoments(response.moments)
                    eventEmitter.emitMoments(MomentsEvent.ListUpdated(response.moments))
                }
            }.onFailure { error -> log.warn(error) { "background moment refresh failed" } }
        } finally {
            inFlightMutex.withLock { inFlightFetches.remove(key) }
        }
    }

    suspend fun syncFromServer(
        apiService: ImApiService,
        databaseService: DatabaseService,
        eventEmitter: SdkEventEmitter,
        pageNumber: Int = 1,
        showNumber: Int = 100,
    ) = fetchMomentListFromServer(
        apiService,
        databaseService,
        eventEmitter,
        ownerUserID = null,
        pageNumber = pageNumber,
        showNumber = showNumber,
    ).moments
}
