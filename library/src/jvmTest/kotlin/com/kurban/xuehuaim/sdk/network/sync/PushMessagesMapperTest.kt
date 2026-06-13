package com.kurban.xuehuaim.sdk.network.sync

import protokt.v1.Bytes
import protokt.v1.openim.sdkws.MsgData
import protokt.v1.openim.sdkws.PullMsgs
import protokt.v1.openim.sdkws.PushMessages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PushMessagesMapperTest {
    @Test
    fun roundTripPushMessagesAndMapToPullMsgResp() {
        val textContent = """{"content":"hello"}"""
        val msg = MsgData {
            clientMsgID = "client-1"
            serverMsgID = "server-1"
            sendID = "user-a"
            recvID = "user-b"
            contentType = 101
            content = Bytes.from(textContent.encodeToByteArray())
            seq = 42
            sendTime = 1_700_000_000_000
            createTime = 1_700_000_000_001
            sessionType = 1
            senderNickname = "Alice"
            senderFaceURL = "https://example.com/a.png"
        }
        val pushMessages = PushMessages {
            msgs = mapOf(
                "si_user-a_user-b" to PullMsgs {
                    msgs = listOf(msg)
                },
            )
        }

        val serialized = pushMessages.serialize()
        val decoded = PushMessages.Deserializer.deserialize(serialized)
        val pullResp = decoded.toPullMsgResp()

        assertEquals(1, pullResp.msgs.size)
        val wsMsg = pullResp.msgs.values.first().msgs.first()
        assertEquals("client-1", wsMsg.clientMsgID)
        assertEquals("server-1", wsMsg.serverMsgID)
        assertEquals("user-a", wsMsg.sendID)
        assertEquals("user-b", wsMsg.recvID)
        assertEquals(101, wsMsg.contentType)
        assertEquals(textContent, wsMsg.content)
        assertEquals(42, wsMsg.seq)
        assertEquals(1_700_000_000_000, wsMsg.sendTime)
        assertEquals(1_700_000_000_001, wsMsg.createTime)
        assertEquals(1, wsMsg.sessionType)
        assertEquals("Alice", wsMsg.senderNickname)
        assertEquals("https://example.com/a.png", wsMsg.senderFaceURL)
    }

    @Test
    fun decodePullMsgsContentStillHandlesBase64Content() {
        val pullResp = PullMsgResp(
            msgs = mapOf(
                "si_a_b" to MsgList(
                    msgs = listOf(
                        WsMsgData(
                            clientMsgID = "c1",
                            content = "eyJjb250ZW50IjoiaGVsbG8ifQ==",
                            contentType = 101,
                        ),
                    ),
                ),
            ),
        )
        val decoded = decodePullMsgsContent(pullResp)
        val content = decoded.msgs.values.first().msgs.first().content
        assertTrue(content?.contains("hello") == true)
    }

    @Test
    fun wireDecoderMatchesProtoktSerialization() {
        val textContent = """{"content":"push"}"""
        val msg = MsgData {
            clientMsgID = "wire-client"
            sendID = "sender"
            contentType = 101
            content = Bytes.from(textContent.encodeToByteArray())
            seq = 7
        }
        val pushMessages = PushMessages {
            msgs = mapOf(
                "si_a_b" to PullMsgs { msgs = listOf(msg) },
            )
        }
        val wireDecoded = PushMessagesWireDecoder.decode(pushMessages.serialize())
        val protoktDecoded = pushMessages.toPullMsgResp()
        assertEquals(protoktDecoded, wireDecoded)
    }
}
