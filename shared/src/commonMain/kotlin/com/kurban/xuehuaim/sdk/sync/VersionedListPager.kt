package com.kurban.xuehuaim.sdk.sync

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.enum.SdkErrorCode
import com.kurban.xuehuaim.sdk.exception.XueHuaException
import com.kurban.xuehuaim.sdk.model.FriendInfo
import com.kurban.xuehuaim.sdk.model.GroupInfo

internal object VersionedListPager {
    private const val FRIEND_VERSION_TABLE = "friend"
    private const val GROUP_VERSION_TABLE = "group"

    suspend fun fetchFriendsPage(
        databaseService: DatabaseService,
        apiService: VersionedListGapFillApi,
        userId: String,
        offset: Int,
        count: Int,
        filterBlack: Boolean,
    ): List<FriendInfo> {
        val versionInfo = databaseService.getVersionSync(FRIEND_VERSION_TABLE, userId)
        val uidList = versionInfo?.uidList.orEmpty()
        if (offset > uidList.size) {
            throw XueHuaException.from(SdkErrorCode.PARAM_ERROR, "offset exceeds the length of the UID list")
        }
        val blackIds = if (filterBlack) databaseService.getBlackUserIds() else emptySet()
        val fetchLimit = if (filterBlack && blackIds.isNotEmpty()) count * 2 else count
        val end = minOf(offset + fetchLimit, uidList.size)
        val paginatedIds = uidList.subList(offset, end)
        if (paginatedIds.isEmpty()) return emptyList()

        var result = fetchMissingFriends(databaseService, apiService, userId, paginatedIds)
        if (!filterBlack || blackIds.isEmpty()) return result

        result = result.filter { it.userID !in blackIds }
        return result.take(count)
    }

    suspend fun fetchJoinedGroupsPage(
        databaseService: DatabaseService,
        apiService: VersionedListGapFillApi,
        userId: String,
        offset: Int,
        count: Int,
    ): List<GroupInfo> {
        val versionInfo = databaseService.getVersionSync(GROUP_VERSION_TABLE, userId)
        val uidList = versionInfo?.uidList.orEmpty()
        if (offset > uidList.size) {
            throw XueHuaException.from(SdkErrorCode.PARAM_ERROR, "offset exceeds the length of the UID list")
        }
        val end = minOf(offset + count, uidList.size)
        val paginatedIds = uidList.subList(offset, end)
        if (paginatedIds.isEmpty()) return emptyList()
        return fetchMissingGroups(databaseService, apiService, paginatedIds)
    }

    private suspend fun fetchMissingFriends(
        databaseService: DatabaseService,
        apiService: VersionedListGapFillApi,
        userId: String,
        ids: List<String>,
    ): List<FriendInfo> {
        var local = databaseService.getFriendsByUserIds(ids)
        val localIds = local.map { it.userID }.toSet()
        val missing = ids.filter { it !in localIds }
        if (missing.isNotEmpty()) {
            val server = apiService.getDesignatedFriends(userId, missing)
            databaseService.batchUpsertFriends(server)
            local = databaseService.getFriendsByUserIds(ids)
        }
        return local
    }

    private suspend fun fetchMissingGroups(
        databaseService: DatabaseService,
        apiService: VersionedListGapFillApi,
        ids: List<String>,
    ): List<GroupInfo> {
        var local = databaseService.getGroupsByGroupIds(ids)
        val localIds = local.map { it.groupID }.toSet()
        val missing = ids.filter { it !in localIds }
        if (missing.isNotEmpty()) {
            val server = apiService.getGroupsInfo(missing)
            databaseService.batchUpsertGroups(server)
            local = databaseService.getGroupsByGroupIds(ids)
        }
        return local
    }
}
