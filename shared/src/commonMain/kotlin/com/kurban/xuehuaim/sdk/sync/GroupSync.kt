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
    ) {
        runCatching {
            val versionInfo = databaseService.getVersionSync(VERSION_TABLE, userId)
            val localVersion = versionInfo?.version ?: 0
            val localVersionId = versionInfo?.versionID.orEmpty()
            val resp = apiService.getIncrementalJoinGroup(userId, localVersion, localVersionId)

            val allGroups = (resp.insert.orEmpty() + resp.update.orEmpty())
            if (resp.full && allGroups.isNotEmpty()) {
                val serverIds = allGroups.map { it.groupID }.toSet()
                databaseService.getAllGroups()
                    .filter { it.groupID !in serverIds }
                    .forEach { removeGroupLocally(databaseService, eventEmitter, it.groupID) }
            }

            resp.delete.orEmpty().forEach { groupId ->
                removeGroupLocally(databaseService, eventEmitter, groupId)
            }

            if (allGroups.isNotEmpty()) {
                databaseService.batchUpsertGroups(allGroups)
                allGroups.forEach { group ->
                    if (group.status == DISMISSED_GROUP_STATUS) {
                        databaseService.deleteGroupMembers(group.groupID)
                        databaseService.markConversationNotInGroup(group.groupID)
                        eventEmitter.emitGroup(GroupEvent.GroupDismissed(group.groupID))
                    } else {
                        eventEmitter.emitGroup(GroupEvent.GroupInfoChanged(group))
                    }
                }
            }

            if (resp.version > 0 || resp.versionID.isNotEmpty()) {
                databaseService.setVersionSync(
                    tableName = VERSION_TABLE,
                    entityId = userId,
                    versionID = resp.versionID,
                    version = resp.version,
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
