package com.kurban.xuehuaim.sdk.sync

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.db.InMemoryImDatabase
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.model.GroupInfo
import com.kurban.xuehuaim.sdk.network.http.IncrementalJoinGroupResp
import com.kurban.xuehuaim.sdk.platform.createDatabaseDriverFactory
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class GroupSyncJvmTest {

    @Test
    fun syncJoinedGroups_fullResponse_pullsGroupsAndUidList() = runBlocking {
        val api = FakeGroupSyncApi(
            incremental = IncrementalJoinGroupResp(
                full = true,
                versionID = "g-version-1",
                version = 2,
            ),
            groups = listOf(
                GroupInfo(groupID = "g1", groupName = "Team A"),
                GroupInfo(groupID = "g2", groupName = "Team B"),
            ),
            fullGroupIDs = listOf("g1", "g2"),
        )
        val databaseService = createTestDatabaseService()
        val eventEmitter = SdkEventEmitter()

        GroupSync.syncJoinedGroups(api, databaseService, eventEmitter, "user1")

        assertEquals(2, databaseService.getAllGroups().size)
        val version = databaseService.getVersionSync("group", "user1")
        assertEquals(listOf("g1", "g2"), version?.uidList)
        assertEquals(1, api.getJoinedGroupListCallCount)
    }

    @Test
    fun syncJoinedGroups_incremental_insertsUpdateUidList() = runBlocking {
        val api = FakeGroupSyncApi(
            incremental = IncrementalJoinGroupResp(
                full = false,
                versionID = "g-version-2",
                version = 2,
                insert = listOf(GroupInfo(groupID = "g3", groupName = "Team C")),
            ),
        )
        val databaseService = createTestDatabaseService()
        databaseService.setVersionSync("group", "user1", "g-version-1", 1, uidList = listOf("g1"))
        val eventEmitter = SdkEventEmitter()

        GroupSync.syncJoinedGroups(api, databaseService, eventEmitter, "user1")

        assertEquals("g3", databaseService.getAllGroups().single().groupID)
        assertEquals(listOf("g1", "g3"), databaseService.getVersionSync("group", "user1")?.uidList)
        assertEquals(0, api.getJoinedGroupListCallCount)
    }

    private suspend fun createTestDatabaseService(): DatabaseService {
        val service = DatabaseService(createDatabaseDriverFactory(), ":memory:test")
        service.bindTestDatabase(InMemoryImDatabase())
        return service
    }

    private class FakeGroupSyncApi(
        private val incremental: IncrementalJoinGroupResp,
        private val groups: List<GroupInfo> = emptyList(),
        private val fullGroupIDs: List<String> = emptyList(),
    ) : GroupSyncApi {
        var getJoinedGroupListCallCount: Int = 0
            private set

        override suspend fun getIncrementalJoinGroup(
            userID: String,
            version: Int,
            versionID: String,
        ): IncrementalJoinGroupResp = incremental

        override suspend fun getJoinedGroupList(
            fromUserID: String,
            pageSize: Int
        ): List<GroupInfo> {
            getJoinedGroupListCallCount++
            return groups
        }

        override suspend fun getFullJoinGroupIDs(userID: String): List<String> = fullGroupIDs
    }
}
