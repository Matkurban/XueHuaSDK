package com.kurban.xuehuaim.sdk.sync

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.db.InMemoryImDatabase
import com.kurban.xuehuaim.sdk.model.ConversationInfo
import com.kurban.xuehuaim.sdk.network.sync.MsgList
import com.kurban.xuehuaim.sdk.platform.createDatabaseDriverFactory
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationMinSeqSyncTest {

    @Test
    fun applyMinSeqFromPull_updatesConversationMinSeqWhenIsEnd() = runBlocking {
        val databaseService = createTestDatabaseService()
        databaseService.insertOrReplaceConversation(
            ConversationInfo(conversationID = "c1", minSeq = 0, maxSeq = 20),
        )

        ConversationMinSeqSync.applyMinSeqFromPull(
            databaseService = databaseService,
            conversationId = "c1",
            msgList = MsgList(msgs = emptyList(), isEnd = true, endSeq = 5),
            isReverse = false,
        )

        assertEquals(5L, databaseService.getConversation("c1")?.minSeq)
    }

    private suspend fun createTestDatabaseService(): DatabaseService {
        val service = DatabaseService(createDatabaseDriverFactory(), ":memory:min-seq")
        service.bindTestDatabase(InMemoryImDatabase())
        return service
    }
}
