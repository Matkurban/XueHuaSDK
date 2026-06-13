package com.kurban.xuehuaim.sdk.manager

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.db.InMemoryImDatabase
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.network.http.ImApiService
import com.kurban.xuehuaim.sdk.network.http.SdkHttpClient
import com.kurban.xuehuaim.sdk.platform.createDatabaseDriverFactory
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadApiNoSyncTest {

    @Test
    fun getFriendList_emptyDb_returnsLocalOnly() = runBlocking {
        val manager = createManager()
        assertEquals(emptyList(), manager.getFriendList())
    }

    @Test
    fun getFriendListPage_emptyDb_returnsEmptyWithoutNetwork() = runBlocking {
        val manager = createManager()
        assertEquals(emptyList(), manager.getFriendListPage())
    }

    @Test
    fun getJoinedGroupList_emptyDb_returnsLocalOnly() = runBlocking {
        val manager = createGroupManager()
        assertEquals(emptyList(), manager.getJoinedGroupList())
    }

    @Test
    fun getJoinedGroupListPage_emptyDb_returnsEmptyWithoutNetwork() = runBlocking {
        val manager = createGroupManager()
        assertEquals(emptyList(), manager.getJoinedGroupListPage())
    }

    private suspend fun createManager(): FriendshipManager {
        val databaseService = createTestDatabaseService()
        return FriendshipManager(
            apiService = ImApiService(SdkHttpClient()),
            databaseService = databaseService,
            eventEmitter = SdkEventEmitter(),
            loginUserId = { "user1" },
        )
    }

    private suspend fun createGroupManager(): GroupManager {
        val databaseService = createTestDatabaseService()
        return GroupManager(
            apiService = ImApiService(SdkHttpClient()),
            databaseService = databaseService,
            eventEmitter = SdkEventEmitter(),
            loginUserId = { "user1" },
        )
    }

    private suspend fun createTestDatabaseService(): DatabaseService {
        val service = DatabaseService(createDatabaseDriverFactory(), ":memory:read-api")
        service.bindTestDatabase(InMemoryImDatabase())
        return service
    }
}
