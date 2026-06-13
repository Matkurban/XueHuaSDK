package com.kurban.xuehuaim.sdk.util

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

internal actual object System {
    actual fun currentTimeMillis(): Long =
        (NSDate().timeIntervalSince1970 * 1000).toLong()
}
