package com.kurban.xuehuaim.sdk.sync

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.db.InMemoryImDatabase
import com.kurban.xuehuaim.sdk.enum.MessageType
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.model.Message
import com.kurban.xuehuaim.sdk.network.http.ConversationsHasReadAndMaxSeqResp
import com.kurban.xuehuaim.sdk.network.http.ImApiService
import com.kurban.xuehuaim.sdk.network.http.SdkHttpClient
import com.kurban.xuehuaim.sdk.network.notify.NotificationDispatcher
import com.kurban.xuehuaim.sdk.network.sync.MsgSyncer
import com.kurban.xuehuaim.sdk.platform.createDatabaseDriverFactory
import com.kurban.xuehuaim.sdk.testutil.createTestWebSocketService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class MsgSyncerJvmTest {

    @Test
    fun loadSeq_restoresSyncedMaxSeqFromDatabase() = runBlocking {
        val databaseService = createTestDatabaseService()
        databaseService.insertOrReplaceMessage(
            Message(
                clientMsgID = "m1",
                conversationID = "c1",
                seq = 12,
                sendTime = 100,
                contentType = MessageType.TEXT,
            ),
        )
        databaseService.setNotificationSeq("n_friend", 4)
        val syncer = createMsgSyncer(databaseService)

        syncer.loadSeq()

        assertEquals(12L, syncer.syncedMaxSeq("c1"))
        assertEquals(4L, syncer.syncedMaxSeq("n_friend"))
    }

    @Test
    fun loadSeq_marksReinstalledWhenNoConversationsAndNotInstalled() = runBlocking {
        val databaseService = createTestDatabaseService()
        val syncer = createMsgSyncer(databaseService)

        syncer.loadSeq()

        assertTrue(syncer.isReinstalled())
    }

    @Test
    fun syncMessageGapsFromServer_completesWithoutDeadlock() = runBlocking {
        val databaseService = createTestDatabaseService()
        databaseService.insertOrReplaceKv(MessageSeqSync.SDK_INSTALLED_KV_KEY, "1", isGlobal = true)
        val apiService = object : ImApiService(SdkHttpClient()) {
            override suspend fun getConversationsHasReadAndMaxSeq(
                userID: String,
                conversationIDs: List<String>,
            ): ConversationsHasReadAndMaxSeqResp = ConversationsHasReadAndMaxSeqResp()
        }
        val syncer = createMsgSyncer(databaseService, apiService)
        syncer.bindUser("user1")
        syncer.loadSeq()

        withTimeout(3.seconds) {
            syncer.syncMessageGapsFromServer(MessageSeqSync.DEFAULT_PULL_NUMS)
        }
    }

    private suspend fun createTestDatabaseService(): DatabaseService {
        val service = DatabaseService(createDatabaseDriverFactory(), ":memory:msg-syncer")
        service.bindTestDatabase(InMemoryImDatabase())
        return service
    }

    private fun createMsgSyncer(
        databaseService: DatabaseService,
        apiService: ImApiService = ImApiService(SdkHttpClient()),
    ): MsgSyncer {
        val eventEmitter = SdkEventEmitter()
        val notificationDispatcher = NotificationDispatcher(
            databaseService = databaseService,
            apiService = apiService,
            eventEmitter = eventEmitter,
            loginUserId = { "user1" },
        )
        return MsgSyncer(
            webSocketService = createTestWebSocketService(eventEmitter),
            databaseService = databaseService,
            apiService = apiService,
            notificationDispatcher = notificationDispatcher,
            eventEmitter = eventEmitter,
        )
    }
}
