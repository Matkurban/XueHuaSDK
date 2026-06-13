package com.kurban.xuehuaim.sdk.util

import kotlin.time.Clock
import kotlin.time.Instant

internal actual object System {
    actual fun currentTimeMillis(): Long {
        val currentMoment: Instant = Clock.System.now()
        return currentMoment.toEpochMilliseconds()
    }
}