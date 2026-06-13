package com.kurban.xuehuaim.sdk.network.ws

import com.kurban.xuehuaim.sdk.platform.GzipCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WsCodecTest {
    private val codec = WsCodec(GzipCodec(), enableCompression = false)

    @Test
    fun decodeResponseWithBase64Payload() {
        val json =
            """{"reqIdentifier":2001,"errCode":0,"errMsg":"","msgIncr":"1","operationID":"op","data":"aGVsbG8="}"""
        val response = codec.decodeResponse(json.encodeToByteArray())
        assertTrue(response.isSuccess)
        assertEquals("hello", response.data.decodeToString())
    }

    @Test
    fun decodeErrorResponseWithNullData() {
        val json =
            """{"reqIdentifier":1002,"msgIncr":"7976982000_1781066965869_1","operationID":"1781066965870","errCode":500,"errMsg":"ServerInternalError","data":null}"""
        val response = codec.decodeResponse(json.encodeToByteArray())
        assertEquals(1002, response.reqIdentifier)
        assertEquals(500, response.errCode)
        assertEquals("ServerInternalError", response.errMsg)
        assertTrue(!response.isSuccess)
        assertEquals(0, response.data.size)
    }
}
