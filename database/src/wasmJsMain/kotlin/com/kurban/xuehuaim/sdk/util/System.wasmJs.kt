package com.kurban.xuehuaim.sdk.util

import kotlin.time.Clock

internal actual object System {
    actual fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
}
