package com.kurban.xuehuaim.sdk.sync

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.db.InMemoryImDatabase
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.model.ConversationInfo
import com.kurban.xuehuaim.sdk.network.http.ConversationsHasReadAndMaxSeqResp
import com.kurban.xuehuaim.sdk.network.http.IncrementalConversationResp
import com.kurban.xuehuaim.sdk.platform.createDatabaseDriverFactory
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ConversationSyncJvmTest {

    @Test
    fun syncIncremental_localVersionZero_doesNotForceFullSync() = runBlocking {
        val databaseService = createTestDatabaseService()
        val api = FakeConversationSyncApi(
            incrementalResp = IncrementalConversationResp(
                full = false,
                versionID = "v1",
                version = 1,
                insert = listOf(
                    ConversationInfo(conversationID = "c1", latestMsgSendTime = 100),
                ),
            ),
            fullConversationIDs = listOf("c1"),
        )
        val emitter = SdkEventEmitter()

        ConversationSync.syncFromServer(api, databaseService, emitter, "user1")

        val version = databaseService.getVersionSync("conversations", "user1")
        assertEquals(1, version?.version)
        assertEquals(listOf("c1"), version?.uidList)
        assertFalse(api.fullSyncCalled)
    }

    @Test
    fun syncIncremental_delete_removesUidFromList() = runBlocking {
        val databaseService = createTestDatabaseService()
        databaseService.setVersionSync(
            tableName = "conversations",
            entityId = "user1",
            versionID = "v1",
            version = 1,
            uidList = listOf("c1", "c2"),
        )
        databaseService.insertOrReplaceConversation(
            ConversationInfo(conversationID = "c2", latestMsgSendTime = 1),
        )
        val api = FakeConversationSyncApi(
            incrementalResp = IncrementalConversationResp(
                full = false,
                versionID = "v2",
                version = 2,
                delete = listOf("c1"),
            ),
        )

        ConversationSync.syncFromServer(api, databaseService, SdkEventEmitter(), "user1")

        assertEquals(
            listOf("c2"),
            databaseService.getVersionSync("conversations", "user1")?.uidList
        )
    }

    private suspend fun createTestDatabaseService(): DatabaseService {
        val service = DatabaseService(createDatabaseDriverFactory(), ":memory:conv-sync")
        service.bindTestDatabase(InMemoryImDatabase())
        return service
    }

    private class FakeConversationSyncApi(
        private val incrementalResp: IncrementalConversationResp,
        private val fullConversationIDs: List<String> = emptyList(),
    ) : ConversationSyncApi {
        var fullSyncCalled = false

        override suspend fun getIncrementalConversations(
            userID: String,
            version: Int,
            versionID: String,
        ): IncrementalConversationResp = incrementalResp

        override suspend fun getFullConversationIDs(userID: String): List<String> =
            fullConversationIDs

        override suspend fun getAllConversations(ownerUserID: String): List<ConversationInfo> {
            fullSyncCalled = true
            return emptyList()
        }

        override suspend fun getConversations(
            ownerUserID: String,
            conversationIDs: List<String>,
        ): List<ConversationInfo> = emptyList()

        override suspend fun getConversationsHasReadAndMaxSeq(
            userID: String,
            conversationIDs: List<String>,
        ): ConversationsHasReadAndMaxSeqResp = ConversationsHasReadAndMaxSeqResp()
    }
}
