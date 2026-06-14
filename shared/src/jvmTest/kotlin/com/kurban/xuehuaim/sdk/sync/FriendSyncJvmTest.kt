package com.kurban.xuehuaim.sdk.sync

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.db.InMemoryImDatabase
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.model.FriendInfo
import com.kurban.xuehuaim.sdk.network.http.FriendInfoDto
import com.kurban.xuehuaim.sdk.network.http.IncrementalFriendsResp
import com.kurban.xuehuaim.sdk.platform.createDatabaseDriverFactory
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class FriendSyncJvmTest {

    @Test
    fun syncFriends_fullResponse_pullsPaginatedFriendListAndUidList() = runBlocking {
        val api = FakeFriendSyncApi(
            incremental = IncrementalFriendsResp(
                full = true,
                versionID = "version-1",
                version = 3,
            ),
            friends = listOf(
                FriendInfo(ownerUserID = "user1", userID = "friend1", nickname = "Alice"),
                FriendInfo(ownerUserID = "user1", userID = "friend2", nickname = "Bob"),
            ),
            fullFriendUserIDs = listOf("friend1", "friend2"),
        )
        val databaseService = createTestDatabaseService()
        val eventEmitter = SdkEventEmitter()

        val changed = FriendSync.syncFriends(api, databaseService, eventEmitter, "user1")

        assertEquals(setOf("friend1", "friend2"), changed)
        assertEquals(2, databaseService.getAllFriends().size)
        val version = databaseService.getVersionSync("friend", "user1")
        assertEquals("version-1", version?.versionID)
        assertEquals(3, version?.version)
        assertEquals(listOf("friend1", "friend2"), version?.uidList)
    }

    @Test
    fun syncFriends_incrementalResponse_upsertsInsertedFriendsAndUpdatesUidList() = runBlocking {
        val api = FakeFriendSyncApi(
            incremental = IncrementalFriendsResp(
                full = false,
                versionID = "version-2",
                version = 2,
                insert = listOf(
                    FriendInfoDto(
                        ownerUserID = "user1",
                        friendUserID = "friend3",
                        nickname = "Carol",
                    ),
                ),
            ),
            fullFriendUserIDs = listOf("friend1", "friend3"),
        )
        val databaseService = createTestDatabaseService()
        databaseService.setVersionSync(
            "friend",
            "user1",
            "version-1",
            1,
            uidList = listOf("friend1"),
        )
        val eventEmitter = SdkEventEmitter()

        val changed = FriendSync.syncFriends(api, databaseService, eventEmitter, "user1")

        assertEquals(setOf("friend3"), changed)
        assertEquals("Carol", databaseService.getFriendByUserId("friend3")?.nickname)
        assertEquals(
            listOf("friend1", "friend3"),
            databaseService.getVersionSync("friend", "user1")?.uidList
        )
        assertEquals(0, api.getFriendListCallCount)
    }

    @Test
    fun syncFriends_localVersionZero_doesNotFullSyncWhenFullFalse() = runBlocking {
        val api = FakeFriendSyncApi(
            incremental = IncrementalFriendsResp(
                full = false,
                versionID = "version-1",
                version = 1,
            ),
            friends = listOf(
                FriendInfo(ownerUserID = "user1", userID = "friend9", nickname = "Zoe"),
            ),
        )
        val databaseService = createTestDatabaseService()
        val eventEmitter = SdkEventEmitter()

        FriendSync.syncFriends(api, databaseService, eventEmitter, "user1")

        assertEquals(0, api.getFriendListCallCount)
        assertEquals(null, databaseService.getFriendByUserId("friend9"))
    }

    @Test
    fun syncFriends_sortVersion_refreshesUidList() = runBlocking {
        val api = FakeFriendSyncApi(
            incremental = IncrementalFriendsResp(
                full = false,
                versionID = "version-3",
                version = 3,
                sortVersion = 5,
            ),
            fullFriendUserIDs = listOf("b", "a"),
        )
        val databaseService = createTestDatabaseService()
        databaseService.setVersionSync(
            "friend",
            "user1",
            "version-2",
            2,
            uidList = listOf("a", "b")
        )
        val eventEmitter = SdkEventEmitter()

        FriendSync.syncFriends(api, databaseService, eventEmitter, "user1")

        assertEquals(listOf("b", "a"), databaseService.getVersionSync("friend", "user1")?.uidList)
    }

    private suspend fun createTestDatabaseService(): DatabaseService {
        val service = DatabaseService(createDatabaseDriverFactory(), ":memory:test")
        service.bindTestDatabase(InMemoryImDatabase())
        return service
    }

    private class FakeFriendSyncApi(
        private val incremental: IncrementalFriendsResp,
        private val friends: List<FriendInfo> = emptyList(),
        private val fullFriendUserIDs: List<String> = emptyList(),
    ) : FriendSyncApi {
        var getFriendListCallCount: Int = 0
            private set

        override suspend fun getIncrementalFriends(
            userID: String,
            version: Int,
            versionID: String,
        ): IncrementalFriendsResp = incremental

        override suspend fun getFriendList(userID: String, pageSize: Int): List<FriendInfo> {
            getFriendListCallCount++
            return friends
        }

        override suspend fun getFullFriendUserIDs(userID: String): List<String> = fullFriendUserIDs
    }
}
