package com.kurban.xuehuaim.sdk.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MomentInfoSerializationTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun deserializeMomentInfoWithMediaArray() {
        val payload = """
            {
              "momentID": "xxx",
              "userID": "yyy",
              "content": "，",
              "media": [{"type": "image", "url": "https://example.com/a.jpg"}],
              "likeCount": 0,
              "commentCount": 0
            }
        """.trimIndent()

        val moment = json.decodeFromString<MomentInfo>(payload)

        assertEquals("xxx", moment.momentID)
        assertEquals(1, moment.media.size)
        assertEquals("image", moment.media[0].type)
        assertEquals("https://example.com/a.jpg", moment.media[0].url)
    }

    @Test
    fun deserializeMomentInfoWithEmptyMedia() {
        val payload = """
            {
              "momentID": "xxx",
              "userID": "yyy",
              "content": "text only",
              "media": [],
              "likeCount": 0,
              "commentCount": 0
            }
        """.trimIndent()

        val moment = json.decodeFromString<MomentInfo>(payload)

        assertTrue(moment.media.isEmpty())
    }

    @Test
    fun deserializeMomentInfoWithNullMedia() {
        val payload = """
            {
              "momentID": "xxx",
              "userID": "yyy",
              "content": "美好的生活从此刻开始。",
              "media": null,
              "likeCount": 0,
              "commentCount": 0
            }
        """.trimIndent()

        val moment = json.decodeFromString<MomentInfo>(payload)

        assertTrue(moment.media.isEmpty())
    }

    @Test
    fun deserializeMomentInfoWithoutMediaField() {
        val payload = """
            {
              "momentID": "xxx",
              "userID": "yyy",
              "content": "text only",
              "likeCount": 0,
              "commentCount": 0
            }
        """.trimIndent()

        val moment = json.decodeFromString<MomentInfo>(payload)

        assertTrue(moment.media.isEmpty())
    }
}
