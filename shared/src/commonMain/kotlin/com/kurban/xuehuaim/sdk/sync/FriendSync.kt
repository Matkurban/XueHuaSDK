package com.kurban.xuehuaim.sdk.sync

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.event.FriendshipEvent
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.network.http.ImApiService
import com.kurban.xuehuaim.sdk.sync.ConversationDisplayEnricher
import com.kurban.xuehuaim.sdk.util.SdkLogger

internal object FriendSync {
    private val log = SdkLogger.tag("FriendSync")
    private const val VERSION_TABLE = "friend"

    suspend fun syncFriends(
        apiService: ImApiService,
        databaseService: DatabaseService,
        eventEmitter: SdkEventEmitter,
        userId: String,
    ): Set<String> {
        val changedFriendIds = mutableSetOf<String>()
        return runCatching {
            val versionInfo = databaseService.getVersionSync(VERSION_TABLE, userId)
            val localVersion = versionInfo?.version ?: 0
            val localVersionId = versionInfo?.versionID.orEmpty()
            val resp = apiService.getIncrementalFriends(userId, localVersion, localVersionId)

            if (resp.full && localVersion > 0) {
                val serverIds = (resp.insert.orEmpty() + resp.update.orEmpty())
                    .map { it.toFriendInfo(userId).userID }
                    .toSet()
                if (serverIds.isNotEmpty()) {
                    databaseService.getAllFriends()
                        .filter { it.userID !in serverIds }
                        .forEach { databaseService.deleteFriend(it.userID) }
                }
            }

            resp.delete.orEmpty().forEach { friendId ->
                databaseService.deleteFriend(friendId)
                changedFriendIds.add(friendId)
            }

            val upsert = (resp.update.orEmpty() + resp.insert.orEmpty())
                .map { it.toFriendInfo(userId) }
            if (upsert.isNotEmpty()) {
                databaseService.batchUpsertFriends(upsert)
                changedFriendIds += upsert.map { it.userID }
            }

            if (resp.version > 0 || resp.versionID.isNotEmpty()) {
                databaseService.setVersionSync(
                    tableName = VERSION_TABLE,
                    entityId = userId,
                    versionID = resp.versionID,
                    version = resp.version,
                )
            }

            upsert.forEach { friend ->
                eventEmitter.emitFriendship(FriendshipEvent.FriendInfoChanged(friend))
                ConversationDisplayEnricher.updateSingleChatDisplay(
                    databaseService = databaseService,
                    eventEmitter = eventEmitter,
                    friend = friend,
                    selfUserId = userId,
                )
            }
            changedFriendIds
        }.getOrElse { error ->
            log.error(error) { "syncFriends failed" }
            changedFriendIds
        }
    }

    suspend fun syncBlackList(
        apiService: ImApiService,
        databaseService: DatabaseService,
        userId: String,
    ) {
        runCatching {
            val serverBlacks = apiService.getBlackList(userId)
            val serverIds = serverBlacks.map { it.blockUserID }.toSet()
            databaseService.getBlackList()
                .filter { it.blockUserID !in serverIds }
                .forEach { databaseService.deleteBlack(it.blockUserID) }
            serverBlacks.forEach { databaseService.insertOrReplaceBlack(it) }
        }.onFailure { error -> log.error(error) { "syncBlackList failed" } }
    }
}
