package com.kurban.xuehuaim.sdk.enum

import kotlin.test.Test
import kotlin.test.assertEquals

class FavoriteTypeTest {
    @Test
    fun favoriteType_valuesAreStable() {
        assertEquals("message", FavoriteType.MESSAGE.value)
        assertEquals("moment_content", FavoriteType.MOMENT_CONTENT.value)
        assertEquals("moment_comment", FavoriteType.MOMENT_COMMENT.value)
        assertEquals("note", FavoriteType.NOTE.value)
        assertEquals("link", FavoriteType.LINK.value)
    }
}
