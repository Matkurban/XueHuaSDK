package com.kurban.xuehuaim.sdk.sync

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.db.InMemoryImDatabase
import com.kurban.xuehuaim.sdk.enum.MessageType
import com.kurban.xuehuaim.sdk.model.ConversationInfo
import com.kurban.xuehuaim.sdk.model.Message
import com.kurban.xuehuaim.sdk.platform.createDatabaseDriverFactory
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationSeqSyncTest {

    @Test
    fun getConversationMaxNormalMsgSeq_ignoresNotifications() = runBlocking {
        val databaseService = createTestDatabaseService()
        databaseService.insertOrReplaceMessage(
            Message(
                clientMsgID = "n1",
                conversationID = "c1",
                seq = 99,
                sendTime = 100,
                contentType = MessageType.FRIEND_ADDED,
            ),
        )
        databaseService.insertOrReplaceMessage(
            Message(
                clientMsgID = "m1",
                conversationID = "c1",
                seq = 5,
                sendTime = 50,
                contentType = MessageType.TEXT,
            ),
        )

        assertEquals(5L, databaseService.getConversationMaxNormalMsgSeq("c1"))
    }

    @Test
    fun unreadCount_derivedFromMaxSeqMinusHasReadSeq() {
        val unread = MessageSeqSync.unreadCountFromSeq(maxSeq = 15, hasReadSeq = 10)
        assertEquals(5, unread)
    }

    private suspend fun createTestDatabaseService(): DatabaseService {
        val service = DatabaseService(createDatabaseDriverFactory(), ":memory:conv-seq")
        service.bindTestDatabase(InMemoryImDatabase())
        service.insertOrReplaceConversation(
            ConversationInfo(conversationID = "c1", latestMsgSendTime = 1),
        )
        return service
    }
}
