package com.kurban.xuehuaim.sdk.util

import okio.ByteString.Companion.toByteString

internal fun md5Hex(text: String): String =
    text.encodeToByteArray().toByteString().md5().hex()
