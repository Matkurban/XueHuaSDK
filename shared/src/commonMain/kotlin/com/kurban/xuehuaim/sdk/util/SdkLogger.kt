package com.kurban.xuehuaim.sdk.util

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging

internal object SdkLogger {
    fun tag(name: String): KLogger = KotlinLogging.logger(name)
}
