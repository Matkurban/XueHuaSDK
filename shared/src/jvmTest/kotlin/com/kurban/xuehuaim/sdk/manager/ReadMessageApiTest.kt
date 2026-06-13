package com.kurban.xuehuaim.sdk.manager

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.db.InMemoryImDatabase
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.model.SearchParams
import com.kurban.xuehuaim.sdk.network.http.ImApiService
import com.kurban.xuehuaim.sdk.network.http.SdkHttpClient
import com.kurban.xuehuaim.sdk.network.notify.NotificationDispatcher
import com.kurban.xuehuaim.sdk.network.sync.MsgSyncer
import com.kurban.xuehuaim.sdk.platform.FileSystem
import com.kurban.xuehuaim.sdk.platform.createDatabaseDriverFactory
import com.kurban.xuehuaim.sdk.testutil.createTestWebSocketService
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReadMessageApiTest {

    @Test
    fun findMessageList_readsLocalDbOnly() = runBlocking {
        val manager = createManager()
        assertEquals(
            emptyList(),
            manager.findMessageList(listOf(SearchParams(clientMsgIDList = listOf("missing")))).messageList,
        )
    }

    @Test
    fun getAdvancedHistoryMessageList_emptyDb_returnsIsEnd() = runBlocking {
        val manager = createManager()
        val result = manager.getAdvancedHistoryMessageList("missing-conv", count = 20)
        assertTrue(result.messageList.isNullOrEmpty())
        assertEquals(true, result.isEnd)
    }

    private suspend fun createManager(): MessageManager {
        val databaseService = createTestDatabaseService()
        val eventEmitter = SdkEventEmitter()
        val apiService = ImApiService(SdkHttpClient())
        val httpClient = SdkHttpClient()
        val notificationDispatcher = NotificationDispatcher(
            databaseService = databaseService,
            apiService = apiService,
            eventEmitter = eventEmitter,
            loginUserId = { "user1" },
        )
        val msgSyncer = MsgSyncer(
            webSocketService = createTestWebSocketService(eventEmitter),
            databaseService = databaseService,
            apiService = apiService,
            notificationDispatcher = notificationDispatcher,
            eventEmitter = eventEmitter,
        )
        return MessageManager(
            apiService = apiService,
            databaseService = databaseService,
            webSocketService = createTestWebSocketService(eventEmitter),
            msgSyncer = msgSyncer,
            notificationDispatcher = notificationDispatcher,
            eventEmitter = eventEmitter,
            loginUserId = { "user1" },
            fileUploadService = FileUploadService(
                apiService = apiService,
                httpClient = httpClient,
                fileSystem = FileSystem(),
                databaseService = databaseService,
                eventEmitter = eventEmitter,
                loginUserId = { "user1" },
            ),
            fileSystem = FileSystem(),
        )
    }

    private suspend fun createTestDatabaseService(): DatabaseService {
        val service = DatabaseService(createDatabaseDriverFactory(), ":memory:read-msg")
        service.bindTestDatabase(InMemoryImDatabase())
        return service
    }
}
