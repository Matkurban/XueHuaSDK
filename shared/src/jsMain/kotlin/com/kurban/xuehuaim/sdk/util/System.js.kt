package com.kurban.xuehuaim.sdk.util

import kotlin.js.Date

internal actual object System {
    actual fun currentTimeMillis(): Long = Date.now().toLong()
}
