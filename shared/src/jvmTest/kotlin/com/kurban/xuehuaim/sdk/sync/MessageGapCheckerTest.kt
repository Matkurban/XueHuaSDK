package com.kurban.xuehuaim.sdk.sync

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.db.InMemoryImDatabase
import com.kurban.xuehuaim.sdk.enum.MessageType
import com.kurban.xuehuaim.sdk.model.Message
import com.kurban.xuehuaim.sdk.platform.createDatabaseDriverFactory
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessageGapCheckerTest {

    @Test
    fun fetchMessagesWithGapCheck_returnsSeqOrderedMessages() = runBlocking {
        val databaseService = createTestDatabaseService()
        databaseService.insertOrReplaceMessage(
            Message(
                clientMsgID = "m3",
                conversationID = "c1",
                seq = 3,
                sendTime = 30,
                contentType = MessageType.TEXT,
            ),
        )
        databaseService.insertOrReplaceMessage(
            Message(
                clientMsgID = "m1",
                conversationID = "c1",
                seq = 1,
                sendTime = 10,
                contentType = MessageType.TEXT,
            ),
        )
        val checker = MessageGapChecker(databaseService) { _, _, _ -> }
        val result = checker.fetchMessagesWithGapCheck("c1", count = 2, startClientMsgId = null)
        assertEquals(listOf(3L, 1L), result.messages.map { it.seq })
    }

    @Test
    fun fetchMessagesWithGapCheck_paginatesOlderMessagesByAnchor() = runBlocking {
        val databaseService = createTestDatabaseService()
        (1..25).forEach { seq ->
            databaseService.insertOrReplaceMessage(
                Message(
                    clientMsgID = "m$seq",
                    conversationID = "c1",
                    seq = seq.toLong(),
                    sendTime = seq.toLong() * 10,
                    contentType = MessageType.TEXT,
                ),
            )
        }
        val checker = MessageGapChecker(databaseService) { _, _, _ -> }
        val firstPage = checker.fetchMessagesWithGapCheck("c1", count = 20, startClientMsgId = null)
        assertEquals(20, firstPage.messages.size)

        val anchor = firstPage.messages.last().clientMsgID
        val secondPage =
            checker.fetchMessagesWithGapCheck("c1", count = 20, startClientMsgId = anchor)
        assertTrue(secondPage.messages.isNotEmpty())
        assertTrue(secondPage.messages.all { it.seq < firstPage.messages.last().seq })
    }

    private suspend fun createTestDatabaseService(): DatabaseService {
        val service = DatabaseService(createDatabaseDriverFactory(), ":memory:gap-checker")
        service.bindTestDatabase(InMemoryImDatabase())
        return service
    }
}
