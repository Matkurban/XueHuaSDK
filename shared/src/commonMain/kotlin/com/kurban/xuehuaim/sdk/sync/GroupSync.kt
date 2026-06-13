package com.kurban.xuehuaim.sdk.sync

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.event.GroupEvent
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.model.GroupInfo
import com.kurban.xuehuaim.sdk.network.http.ImApiService
import com.kurban.xuehuaim.sdk.util.SdkLogger

internal object GroupSync {
    private val log = SdkLogger.tag("GroupSync")
    private const val VERSION_TABLE = "group"
    private const val DISMISSED_GROUP_STATUS = 3

    suspend fun syncJoinedGroups(
        apiService: ImApiService,
        databaseService: DatabaseService,
        eventEmitter: SdkEventEmitter,
        userId: String,
    ) = syncJoinedGroups(apiService as GroupSyncApi, databaseService, eventEmitter, userId)

    suspend fun syncJoinedGroups(
        apiService: GroupSyncApi,
        databaseService: DatabaseService,
        eventEmitter: SdkEventEmitter,
        userId: String,
    ) = RelationSyncMutex.withLock {
        runCatching {
            val versionInfo = databaseService.getVersionSync(VERSION_TABLE, userId)
            val localVersion = versionInfo?.version ?: 0
            val localVersionId = versionInfo?.versionID.orEmpty()
            var uidList = versionInfo?.uidList.orEmpty().toMutableList()
            val resp = apiService.getIncrementalJoinGroup(userId, localVersion, localVersionId)

            if (resp.full) {
                log.info { "group full sync (version=${resp.version})" }
                syncFull(apiService, databaseService, eventEmitter, userId, resp.versionID, resp.version)
                return@runCatching
            }

            resp.delete.orEmpty().forEach { groupId ->
                removeGroupLocally(databaseService, eventEmitter, groupId)
                uidList.remove(groupId)
            }

            val inserts = resp.insert.orEmpty()
            val updates = resp.update.orEmpty()
            val allGroups = updates + inserts
            if (allGroups.isNotEmpty()) {
                databaseService.batchUpsertGroups(allGroups)
                emitGroupChanges(databaseService, eventEmitter, inserts, updates)
            }

            inserts.forEach { group ->
                if (group.groupID !in uidList) uidList.add(group.groupID)
            }

            if (resp.sortVersion > 0) {
                uidList = apiService.getFullJoinGroupIDs(userId).toMutableList()
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
        }.onFailure { error -> log.error(error) { "syncJoinedGroups failed" } }
    }

    suspend fun ensureGroupMembersSynced(
        apiService: ImApiService,
        databaseService: DatabaseService,
        groupId: String,
    ) {
        val existing = databaseService.getGroupMembersPage(groupId, offset = 0, count = 1)
        if (existing.isNotEmpty()) return
        syncGroupInfoAndMembers(apiService, databaseService, groupId)
    }

    suspend fun syncGroupInfoAndMembers(
        apiService: ImApiService,
        databaseService: DatabaseService,
        groupId: String,
    ) {
        runCatching {
            val groups = apiService.getGroupsInfo(listOf(groupId))
            if (groups.isNotEmpty()) {
                databaseService.batchUpsertGroups(groups)
            }
            val members = apiService.getGroupMembers(groupId)
            databaseService.deleteGroupMembers(groupId)
            databaseService.batchUpsertGroupMembers(members)
        }.onFailure { error ->
            log.error(error) { "syncGroupInfoAndMembers failed for $groupId" }
        }
    }

    private suspend fun syncFull(
        apiService: GroupSyncApi,
        databaseService: DatabaseService,
        eventEmitter: SdkEventEmitter,
        userId: String,
        versionID: String,
        version: Int,
    ) {
        databaseService.deleteAllGroups()
        val groups = apiService.getJoinedGroupList(userId)
        if (groups.isNotEmpty()) {
            databaseService.batchUpsertGroups(groups)
            emitGroupChanges(databaseService, eventEmitter, groups, emptyList())
        }
        val uidList = apiService.getFullJoinGroupIDs(userId)
        if (version > 0 || versionID.isNotEmpty()) {
            databaseService.setVersionSync(
                tableName = VERSION_TABLE,
                entityId = userId,
                versionID = versionID,
                version = version,
                uidList = uidList,
            )
        }
        log.info { "group full sync finished: count=${groups.size}" }
    }

    private suspend fun emitGroupChanges(
        databaseService: DatabaseService,
        eventEmitter: SdkEventEmitter,
        inserts: List<GroupInfo>,
        updates: List<GroupInfo>,
    ) {
        (inserts + updates).forEach { group ->
            if (group.status == DISMISSED_GROUP_STATUS) {
                databaseService.deleteGroupMembers(group.groupID)
                databaseService.markConversationNotInGroup(group.groupID)
                eventEmitter.emitGroup(GroupEvent.GroupDismissed(group.groupID))
            } else {
                eventEmitter.emitGroup(GroupEvent.GroupInfoChanged(group))
            }
        }
    }

    private suspend fun removeGroupLocally(
        databaseService: DatabaseService,
        eventEmitter: SdkEventEmitter,
        groupId: String,
    ) {
        databaseService.deleteGroupMembers(groupId)
        databaseService.deleteGroup(groupId)
        databaseService.markConversationNotInGroup(groupId)
        eventEmitter.emitGroup(GroupEvent.GroupDismissed(groupId))
    }
}
