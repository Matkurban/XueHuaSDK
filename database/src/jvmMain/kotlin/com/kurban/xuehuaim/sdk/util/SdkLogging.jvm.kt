package com.kurban.xuehuaim.sdk.util

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import com.kurban.xuehuaim.sdk.config.LogLevel
import org.slf4j.LoggerFactory

internal actual fun configureSdkLogging(level: LogLevel) {
    val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
    root.level = level.toLogbackLevel()
}

private fun LogLevel.toLogbackLevel(): Level = when (this) {
    LogLevel.NONE -> Level.OFF
    LogLevel.ERROR -> Level.ERROR
    LogLevel.WARN -> Level.WARN
    LogLevel.INFO -> Level.INFO
    LogLevel.DEBUG -> Level.DEBUG
    LogLevel.ALL -> Level.TRACE
}
