package com.kurban.xuehuaim.sdk.sync

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.db.InMemoryImDatabase
import com.kurban.xuehuaim.sdk.enum.SdkErrorCode
import com.kurban.xuehuaim.sdk.exception.XueHuaException
import com.kurban.xuehuaim.sdk.model.FriendInfo
import com.kurban.xuehuaim.sdk.model.GroupInfo
import com.kurban.xuehuaim.sdk.platform.createDatabaseDriverFactory
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VersionedListPagerTest {

    @Test
    fun fetchFriendsPage_returnsUidListOrder() = runBlocking {
        val databaseService = createTestDatabaseService()
        databaseService.setVersionSync(
            "friend",
            "user1",
            "v1",
            1,
            uidList = listOf("b", "a", "c"),
        )
        databaseService.batchUpsertFriends(
            listOf(
                FriendInfo(ownerUserID = "user1", userID = "a", nickname = "A"),
                FriendInfo(ownerUserID = "user1", userID = "b", nickname = "B"),
            ),
        )

        val page = VersionedListPager.fetchFriendsPage(
            databaseService = databaseService,
            apiService = NoopGapFillApi,
            userId = "user1",
            offset = 0,
            count = 2,
            filterBlack = false,
        )

        assertEquals(listOf("b", "a"), page.map { it.userID })
    }

    @Test
    fun fetchFriendsPage_offsetBeyondUidList_throws() = runBlocking {
        val databaseService = createTestDatabaseService()
        databaseService.setVersionSync("friend", "user1", "v1", 1, uidList = listOf("a"))

        val error = assertFailsWith<XueHuaException> {
            VersionedListPager.fetchFriendsPage(
                databaseService = databaseService,
                apiService = NoopGapFillApi,
                userId = "user1",
                offset = 5,
                count = 10,
                filterBlack = false,
            )
        }
        assertEquals(SdkErrorCode.PARAM_ERROR.code, error.code)
    }

    @Test
    fun fetchFriendsPage_gapFillFetchesMissingFromServer() = runBlocking {
        val databaseService = createTestDatabaseService()
        databaseService.setVersionSync("friend", "user1", "v1", 1, uidList = listOf("a", "b"))
        databaseService.batchUpsertFriends(
            listOf(FriendInfo(ownerUserID = "user1", userID = "a", nickname = "A")),
        )
        val api = RecordingGapFillApi(
            designatedFriends = listOf(
                FriendInfo(
                    ownerUserID = "user1",
                    userID = "b",
                    nickname = "B"
                )
            ),
        )

        val page = VersionedListPager.fetchFriendsPage(
            databaseService = databaseService,
            apiService = api,
            userId = "user1",
            offset = 0,
            count = 2,
            filterBlack = false,
        )

        assertEquals(listOf("a", "b"), page.map { it.userID })
        assertEquals(listOf("b"), api.designatedFriendRequests)
    }

    private suspend fun createTestDatabaseService(): DatabaseService {
        val service = DatabaseService(createDatabaseDriverFactory(), ":memory:test-pager")
        service.bindTestDatabase(InMemoryImDatabase())
        return service
    }

    private object NoopGapFillApi : VersionedListGapFillApi {
        override suspend fun getDesignatedFriends(
            ownerUserID: String,
            friendUserIDs: List<String>,
        ): List<FriendInfo> = error("unexpected designated friends request: $friendUserIDs")

        override suspend fun getGroupsInfo(groupIDs: List<String>): List<GroupInfo> =
            error("unexpected groups info request: $groupIDs")
    }

    private class RecordingGapFillApi(
        private val designatedFriends: List<FriendInfo> = emptyList(),
    ) : VersionedListGapFillApi {
        val designatedFriendRequests = mutableListOf<String>()

        override suspend fun getDesignatedFriends(
            ownerUserID: String,
            friendUserIDs: List<String>,
        ): List<FriendInfo> {
            designatedFriendRequests += friendUserIDs
            return designatedFriends.filter { it.userID in friendUserIDs }
        }

        override suspend fun getGroupsInfo(groupIDs: List<String>): List<GroupInfo> = emptyList()
    }
}
