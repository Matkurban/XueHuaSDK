package com.kurban.xuehuaim.sdk.util

import com.kurban.xuehuaim.sdk.enum.MessageType
import com.kurban.xuehuaim.sdk.model.AtTextElem
import com.kurban.xuehuaim.sdk.model.Message
import com.kurban.xuehuaim.sdk.model.PictureElem
import com.kurban.xuehuaim.sdk.model.PictureInfo
import com.kurban.xuehuaim.sdk.model.QuoteElem
import com.kurban.xuehuaim.sdk.model.TextElem
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MessageContentParserTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun withParsedContent_parsesTextMessage() {
        val content = json.encodeToString(TextElem("你好"))
        val message = Message(
            clientMsgID = "1",
            contentType = MessageType.TEXT,
            content = content,
        )

        val parsed = message.withParsedContent()

        assertEquals("你好", parsed.textElem?.content)
    }

    @Test
    fun withParsedContent_parsesPictureMessage() {
        val elem = PictureElem(sourcePicture = PictureInfo(url = "https://example.com/a.jpg"))
        val message = Message(
            clientMsgID = "2",
            contentType = MessageType.PICTURE,
            content = json.encodeToString(elem),
        )

        val parsed = message.withParsedContent()

        assertEquals("https://example.com/a.jpg", parsed.pictureElem?.sourcePicture?.url)
    }

    @Test
    fun withParsedContent_parsesAtTextMessage() {
        val elem = AtTextElem(text = "@张三 你好", atUserList = listOf("u1"))
        val message = Message(
            clientMsgID = "3",
            contentType = MessageType.AT_TEXT,
            content = json.encodeToString(elem),
        )

        val parsed = message.withParsedContent()

        assertEquals("@张三 你好", parsed.atTextElem?.text)
    }

    @Test
    fun withParsedContent_parsesQuoteMessage() {
        val elem = QuoteElem(text = "回复内容")
        val message = Message(
            clientMsgID = "4",
            contentType = MessageType.QUOTE,
            content = json.encodeToString(elem),
        )

        val parsed = message.withParsedContent()

        assertEquals("回复内容", parsed.quoteElem?.text)
        assertEquals("回复内容", parsed.textElem?.content)
    }

    @Test
    fun withParsedContent_skipsWhenElemAlreadyPresent() {
        val message = Message(
            clientMsgID = "5",
            contentType = MessageType.TEXT,
            content = """{"content":"ignored"}""",
            textElem = TextElem("已有内容"),
        )

        val parsed = message.withParsedContent()

        assertEquals("已有内容", parsed.textElem?.content)
    }

    @Test
    fun withParsedContent_keepsOriginalOnInvalidJson() {
        val message = Message(
            clientMsgID = "6",
            contentType = MessageType.TEXT,
            content = "not-json",
        )

        val parsed = message.withParsedContent()

        assertEquals("not-json", parsed.content)
        assertEquals(null, parsed.textElem)
    }

    @Test
    fun withParsedContent_parsesMergerMessage() {
        val content =
            """{"title":"A和B的聊天记录","abstractList":["A：hello","B：hi"],"multiMessage":[]}"""
        val message = Message(
            clientMsgID = "m1",
            contentType = MessageType.MERGER,
            content = content,
        )

        val parsed = message.withParsedContent()

        assertEquals("A和B的聊天记录", parsed.mergeElem?.title)
        assertEquals(2, parsed.mergeElem?.abstractList?.size)
    }

    @Test
    fun mergeConversation_preservesShowNameWhenIncomingBlank() {
        val existing = com.kurban.xuehuaim.sdk.model.ConversationInfo(
            conversationID = "si_a_b",
            showName = "张三",
            faceURL = "https://example.com/a.jpg",
            userID = "peer",
        )
        val incoming = existing.copy(showName = null, faceURL = "", latestMsgSendTime = 100L)

        val merged = mergeConversation(existing, incoming)

        assertEquals("张三", merged.showName)
        assertEquals("https://example.com/a.jpg", merged.faceURL)
        assertEquals("peer", merged.userID)
    }

    @Test
    fun parsePeerUserIdFromConversationId_returnsOtherUser() {
        val peerId = parsePeerUserIdFromConversationId("si_userA_userB", "userA")
        assertEquals("userB", peerId)
    }

    @Test
    fun withParsedLatestMsg_parsesConversationLatestMessage() {
        val latest = Message(
            clientMsgID = "7",
            contentType = MessageType.TEXT,
            content = json.encodeToString(TextElem("预览")),
        )
        val conversation = com.kurban.xuehuaim.sdk.model.ConversationInfo(
            conversationID = "si_a_b",
            latestMsg = latest,
        )

        val parsed = conversation.withParsedLatestMsg()

        val latestMsg = requireNotNull(parsed.latestMsg)
        assertNotNull(latestMsg.textElem)
        assertEquals("预览", latestMsg.textElem?.content)
    }
}
