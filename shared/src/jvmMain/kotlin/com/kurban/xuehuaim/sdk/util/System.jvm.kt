package com.kurban.xuehuaim.sdk.util

internal actual object System {
    actual fun currentTimeMillis(): Long = java.lang.System.currentTimeMillis()
}
