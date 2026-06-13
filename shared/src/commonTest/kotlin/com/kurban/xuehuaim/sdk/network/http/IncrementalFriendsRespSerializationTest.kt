package com.kurban.xuehuaim.sdk.network.http

import com.kurban.xuehuaim.sdk.model.ApiResponse
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IncrementalFriendsRespSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun incrementalFriendsResp_parsesNullDeleteInsertUpdate() {
        val raw = """
            {"errCode":0,"errMsg":"","errDlt":"","data":{"version":20,"versionID":"69ee17b90ac447ba334c79cd","full":true,"delete":null,"insert":null,"update":null}}
        """.trimIndent()

        val resp = json.decodeFromString<ApiResponse<IncrementalFriendsResp>>(raw)
        val data = requireNotNull(resp.data)

        assertTrue(resp.isSuccess)
        assertTrue(data.full)
        assertEquals(20, data.version)
        assertEquals("69ee17b90ac447ba334c79cd", data.versionID)
        assertEquals(emptyList(), data.delete.orEmpty())
        assertEquals(emptyList(), data.insert.orEmpty())
        assertEquals(emptyList(), data.update.orEmpty())
    }

    @Test
    fun incrementalFriendsResp_parsesSortVersion() {
        val raw = """
            {"errCode":0,"errMsg":"","errDlt":"","data":{"version":3,"versionID":"v3","full":false,"sortVersion":2,"delete":null,"insert":null,"update":null}}
        """.trimIndent()

        val data = requireNotNull(json.decodeFromString<ApiResponse<IncrementalFriendsResp>>(raw).data)
        assertEquals(2, data.sortVersion)
    }
}
