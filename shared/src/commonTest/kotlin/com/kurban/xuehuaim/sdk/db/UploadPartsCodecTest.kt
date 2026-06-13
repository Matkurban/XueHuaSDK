package com.kurban.xuehuaim.sdk.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UploadPartsCodecTest {
    @Test
    fun encodeAndDecodeRoundTrip() {
        val encoded = UploadPartsCodec.encode(listOf(1, 2, 5))
        assertEquals("[1,2,5]", encoded)
        assertEquals(listOf(1, 2, 5), UploadPartsCodec.decode(encoded))
    }

    @Test
    fun decodeEmptyAndInvalid() {
        assertEquals(emptyList(), UploadPartsCodec.decode(null))
        assertEquals(emptyList(), UploadPartsCodec.decode(""))
        assertEquals(emptyList(), UploadPartsCodec.decode("not-json"))
    }

    @Test
    fun encodeEmptyReturnsNull() {
        assertNull(UploadPartsCodec.encode(emptyList()))
    }
}
