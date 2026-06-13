package com.kurban.xuehuaim.sdk.network.http

import com.kurban.xuehuaim.sdk.model.ApiResponse
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IncrementalJoinGroupRespSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun incrementalJoinGroupResp_parsesNullDeleteInsertUpdateAndSortVersion() {
        val raw = """
            {"errCode":0,"errMsg":"","errDlt":"","data":{"version":20,"versionID":"69ee17b90ac447ba334c79cd","full":true,"sortVersion":3,"delete":null,"insert":null,"update":null}}
        """.trimIndent()

        val resp = json.decodeFromString<ApiResponse<IncrementalJoinGroupResp>>(raw)
        val data = requireNotNull(resp.data)

        assertTrue(resp.isSuccess)
        assertTrue(data.full)
        assertEquals(20, data.version)
        assertEquals(3, data.sortVersion)
        assertEquals(emptyList(), data.insert.orEmpty())
    }
}
