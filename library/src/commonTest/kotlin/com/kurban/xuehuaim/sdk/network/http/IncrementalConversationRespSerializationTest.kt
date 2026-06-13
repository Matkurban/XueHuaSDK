package com.kurban.xuehuaim.sdk.network.http

import com.kurban.xuehuaim.sdk.model.ApiResponse
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IncrementalConversationRespSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun incrementalConversationResp_parsesNullDeleteInsertUpdate() {
        val raw = """
            {"errCode":0,"errMsg":"","errDlt":"","data":{"version":20,"versionID":"69ee17b90ac447ba334c79cd","full":true,"delete":null,"insert":null,"update":null}}
        """.trimIndent()

        val resp = json.decodeFromString<ApiResponse<IncrementalConversationResp>>(raw)
        val data = requireNotNull(resp.data)

        assertTrue(resp.isSuccess)
        assertTrue(data.full)
        assertEquals(20, data.version)
        assertEquals("69ee17b90ac447ba334c79cd", data.versionID)
        assertEquals(emptyList(), data.delete.orEmpty())
        assertEquals(emptyList(), data.insert.orEmpty())
        assertEquals(emptyList(), data.update.orEmpty())
    }
}
