package com.kurban.xuehuaim.sdk.network.sync

import openim.sdkws.MsgData
import openim.sdkws.UserSendMsgResp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SendMessageCodecTest {
    @Test
    fun encodeSendMsgReqProducesValidMsgData() {
        val content = """{"content":"hello"}"""
        val req = SendMsgReqData(
            sendID = "user-a",
            recvID = "user-b",
            groupID = "",
            clientMsgID = "client-1",
            senderPlatformID = 3,
            senderNickname = "Alice",
            senderFaceURL = "https://example.com/a.png",
            sessionType = 1,
            msgFrom = 100,
            contentType = 101,
            contentBytes = content.encodeToByteArray(),
            createTime = 1_700_000_000_000,
            atUserIDList = listOf("user-c"),
        )

        val decoded = MsgData.ADAPTER.decode(encodeSendMsgReq(req))

        assertEquals("user-a", decoded.sendID)
        assertEquals("user-b", decoded.recvID)
        assertEquals("client-1", decoded.clientMsgID)
        assertEquals(3, decoded.senderPlatformID)
        assertEquals("Alice", decoded.senderNickname)
        assertEquals(1, decoded.sessionType)
        assertEquals(100, decoded.msgFrom)
        assertEquals(101, decoded.contentType)
        assertEquals(content, decoded.content.utf8())
        assertEquals(1_700_000_000_000, decoded.createTime)
        assertEquals(listOf("user-c"), decoded.atUserIDList)
    }

    @Test
    fun decodeUserSendMsgRespRoundTrip() {
        val serialized = UserSendMsgResp.ADAPTER.encode(
            UserSendMsgResp(
                serverMsgID = "server-1",
                clientMsgID = "client-1",
                sendTime = 1_700_000_000_123,
            ),
        )

        val decoded = decodeUserSendMsgResp(serialized)

        assertEquals("server-1", decoded?.serverMsgID)
        assertEquals("client-1", decoded?.clientMsgID)
        assertEquals(1_700_000_000_123, decoded?.sendTime)
    }

    @Test
    fun decodeUserSendMsgRespReturnsNullOnGarbage() {
        assertNull(decodeUserSendMsgResp(byteArrayOf(-1, -1, -1, -1, -1)))
    }
}
