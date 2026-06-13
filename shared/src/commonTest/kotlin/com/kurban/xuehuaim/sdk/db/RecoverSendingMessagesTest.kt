package com.kurban.xuehuaim.sdk.db

import com.kurban.xuehuaim.sdk.enum.MessageStatus
import com.kurban.xuehuaim.sdk.enum.MessageType
import com.kurban.xuehuaim.sdk.model.Message
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecoverSendingMessagesTest {
    private val database = InMemoryImDatabase()

    @Test
    fun sendingRecordClearedAfterRecovery() = runBlocking {
        val clientMsgId = "client-msg-1"
        database.insertOrReplaceMessage(
            Message(
                clientMsgID = clientMsgId,
                contentType = MessageType.TEXT,
                content = """{"content":"hi"}""",
                status = MessageStatus.SENDING,
                conversationID = "single_self_peer",
            ),
        )
        database.insertOrReplaceSendingMessage(
            SendingMessage(clientMsgID = clientMsgId, conversationID = "single_self_peer"),
        )

        val message = database.selectMessageByClientMsgId(clientMsgId)!!
        database.insertOrReplaceMessage(message.copy(status = MessageStatus.SEND_FAILED))
        database.deleteSendingMessage(clientMsgId)

        assertEquals(MessageStatus.SEND_FAILED, database.selectMessageByClientMsgId(clientMsgId)?.status)
        assertTrue(database.selectSendingMessages().isEmpty())
    }
}
