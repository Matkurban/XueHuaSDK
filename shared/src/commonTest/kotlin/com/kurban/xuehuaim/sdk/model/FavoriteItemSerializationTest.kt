package com.kurban.xuehuaim.sdk.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FavoriteItemSerializationTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun deserializeFavoriteItemWithDatetimeStringCreateTime() {
        val payload = """
            {
              "favoriteID": "fav-1",
              "userID": "user-1",
              "targetType": "message",
              "targetID": "msg-1",
              "createTime": "2024-06-14T10:30:00"
            }
        """.trimIndent()

        val item = json.decodeFromString<FavoriteItem>(payload)

        assertEquals("fav-1", item.favoriteID)
        assertNotNull(item.createTime)
    }

    @Test
    fun deserializeFavoriteItemWithSpaceSeparatedDatetimeCreateTime() {
        val payload = """
            {
              "favoriteID": "fav-2",
              "userID": "user-1",
              "targetType": "message",
              "targetID": "msg-2",
              "createTime": "2024-06-14 10:30:00"
            }
        """.trimIndent()

        val item = json.decodeFromString<FavoriteItem>(payload)

        assertNotNull(item.createTime)
    }

    @Test
    fun deserializeFavoriteItemWithEpochMillisCreateTime() {
        val payload = """
            {
              "favoriteID": "fav-3",
              "userID": "user-1",
              "targetType": "message",
              "targetID": "msg-3",
              "createTime": 1700000000000
            }
        """.trimIndent()

        val item = json.decodeFromString<FavoriteItem>(payload)

        assertEquals(1_700_000_000_000, item.createTime)
    }

    @Test
    fun deserializeFavoriteListResponseEnvelope() {
        val payload = """
            {
              "errCode": 0,
              "errMsg": "",
              "data": {
                "total": 1,
                "favorites": [
                  {
                    "favoriteID": "fav-1",
                    "userID": "user-1",
                    "targetType": "message",
                    "targetID": "msg-1",
                    "createTime": "2024-06-14T10:30:00"
                  }
                ]
              }
            }
        """.trimIndent()

        val resp = json.decodeFromString<ApiResponse<FavoriteListResponse>>(payload)

        assertEquals(1, resp.data?.favorites?.size)
        assertNotNull(resp.data?.favorites?.first()?.createTime)
    }

    @Test
    fun deserializeFavoriteItemWithNullCreateTime() {
        val payload = """
            {
              "favoriteID": "fav-4",
              "userID": "user-1",
              "targetType": "message",
              "targetID": "msg-4"
            }
        """.trimIndent()

        val item = json.decodeFromString<FavoriteItem>(payload)

        assertNull(item.createTime)
    }
}
