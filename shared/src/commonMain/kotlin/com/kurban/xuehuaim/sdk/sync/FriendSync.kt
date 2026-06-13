package com.kurban.xuehuaim.sdk.sync

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.event.FriendshipEvent
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.model.FriendInfo
import com.kurban.xuehuaim.sdk.network.http.ImApiService
import com.kurban.xuehuaim.sdk.network.http.FriendInfoDto
import com.kurban.xuehuaim.sdk.util.SdkLogger

internal object FriendSync {
    private val log = SdkLogger.tag("FriendSync")
    private const val VERSION_TABLE = "friend"

    suspend fun syncFriends(
        apiService: ImApiService,
        databaseService: DatabaseService,
        eventEmitter: SdkEventEmitter,
        userId: String,
    ): Set<String> = syncFriends(apiService as FriendSyncApi, databaseService, eventEmitter, userId)

    suspend fun syncFriends(
        apiService: FriendSyncApi,
        databaseService: DatabaseService,
        eventEmitter: SdkEventEmitter,
        userId: String,
    ): Set<String> = RelationSyncMutex.withLock {
        val changedFriendIds = mutableSetOf<String>()
        runCatching {
            val versionInfo = databaseService.getVersionSync(VERSION_TABLE, userId)
            val localVersion = versionInfo?.version ?: 0
            val localVersionId = versionInfo?.versionID.orEmpty()
            var uidList = versionInfo?.uidList.orEmpty().toMutableList()
            val resp = apiService.getIncrementalFriends(userId, localVersion, localVersionId)

            if (resp.full) {
                log.info { "friend full sync (version=${resp.version})" }
                return@runCatching syncFull(
                    apiService = apiService,
                    databaseService = databaseService,
                    eventEmitter = eventEmitter,
                    userId = userId,
                    versionID = resp.versionID,
                    version = resp.version,
                )
            }

            resp.delete.orEmpty().forEach { friendId ->
                databaseService.deleteFriend(friendId)
                uidList.remove(friendId)
                changedFriendIds.add(friendId)
                eventEmitter.emitFriendship(FriendshipEvent.FriendDeleted(friendId))
            }

            val inserts = resp.insert.orEmpty().map { it.toFriendInfo(userId) }
            val updates = resp.update.orEmpty().map { it.toFriendInfo(userId) }
            val upsert = updates + inserts
            if (upsert.isNotEmpty()) {
                databaseService.batchUpsertFriends(upsert)
                changedFriendIds += upsert.map { it.userID }
            }

            inserts.forEach { friend ->
                if (friend.userID !in uidList) uidList.add(friend.userID)
                eventEmitter.emitFriendship(FriendshipEvent.FriendAdded(friend))
                enrichSingleChat(databaseService, eventEmitter, userId, friend)
            }
            updates.forEach { friend ->
                eventEmitter.emitFriendship(FriendshipEvent.FriendInfoChanged(friend))
                enrichSingleChat(databaseService, eventEmitter, userId, friend)
            }

            if (resp.sortVersion > 0) {
                uidList = apiService.getFullFriendUserIDs(userId).toMutableList()
            }

            if (resp.version > 0 || resp.versionID.isNotEmpty()) {
                databaseService.setVersionSync(
                    tableName = VERSION_TABLE,
                    entityId = userId,
                    versionID = resp.versionID,
                    version = resp.version,
                    uidList = uidList,
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

    private suspend fun syncFull(
        apiService: FriendSyncApi,
        databaseService: DatabaseService,
        eventEmitter: SdkEventEmitter,
        userId: String,
        versionID: String,
        version: Int,
    ): Set<String> {
        databaseService.deleteAllFriends()
        val friends = apiService.getFriendList(userId)
        if (friends.isNotEmpty()) {
            databaseService.batchUpsertFriends(friends)
        }
        val uidList = apiService.getFullFriendUserIDs(userId)
        if (version > 0 || versionID.isNotEmpty()) {
            databaseService.setVersionSync(
                tableName = VERSION_TABLE,
                entityId = userId,
                versionID = versionID,
                version = version,
                uidList = uidList,
            )
        }
        friends.forEach { friend ->
            eventEmitter.emitFriendship(FriendshipEvent.FriendAdded(friend))
            enrichSingleChat(databaseService, eventEmitter, userId, friend)
        }
        log.info { "friend full sync finished: count=${friends.size}" }
        return friends.map { it.userID }.toSet()
    }

    private suspend fun enrichSingleChat(
        databaseService: DatabaseService,
        eventEmitter: SdkEventEmitter,
        userId: String,
        friend: FriendInfo,
    ) {
        ConversationDisplayEnricher.updateSingleChatDisplay(
            databaseService = databaseService,
            eventEmitter = eventEmitter,
            friend = friend,
            selfUserId = userId,
        )
    }
}
