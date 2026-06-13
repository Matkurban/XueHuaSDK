package com.kurban.xuehuaim.sdk.network.http

import com.kurban.xuehuaim.sdk.model.SendRedPacketRequest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RevokeMsgSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun revokeMsgReq_serializesRequiredFields() {
        val req = RevokeMsgReq(
            userID = "user1",
            conversationID = "si_user1_user2",
            seq = 42L,
        )
        val encoded = json.encodeToString(req)
        assertTrue(encoded.contains("\"userID\":\"user1\""))
        assertTrue(encoded.contains("\"conversationID\":\"si_user1_user2\""))
        assertTrue(encoded.contains("\"seq\":42"))
    }
}

class RedPacketApiModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun sendRedPacketRequest_serializesPacketFields() {
        val req = SendRedPacketRequest(
            packetType = 1,
            totalAmount = 10.0,
            totalCount = 5,
            greeting = "hello",
            convID = "si_a_b",
            targetUserID = "user2",
        )
        val encoded = json.encodeToString(req)
        assertTrue(encoded.contains("\"packetType\":1"))
        assertTrue(encoded.contains("\"totalAmount\":10"))
        assertTrue(encoded.contains("\"greeting\":\"hello\""))
    }

    @Test
    fun grabRedPacketResp_deserializesAmount() {
        val resp = json.decodeFromString<GrabRedPacketResp>("""{"amount":1.23}""")
        assertEquals(1.23, resp.amount)
    }
}
