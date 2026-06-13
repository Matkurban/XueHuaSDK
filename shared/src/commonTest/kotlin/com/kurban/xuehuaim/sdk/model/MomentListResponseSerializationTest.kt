package com.kurban.xuehuaim.sdk.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class MomentListResponseSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun momentListResponse_roundTrip() {
        val response = MomentListResponse(
            total = 2,
            moments = listOf(
                MomentInfo(momentID = "m1", userID = "u1", content = "hello"),
                MomentInfo(momentID = "m2", userID = "u2", content = "world"),
            ),
        )
        val encoded = json.encodeToString(response)
        val decoded = json.decodeFromString<MomentListResponse>(encoded)
        assertEquals(response.total, decoded.total)
        assertEquals(response.moments.size, decoded.moments.size)
        assertEquals("m1", decoded.moments.first().momentID)
    }
}
