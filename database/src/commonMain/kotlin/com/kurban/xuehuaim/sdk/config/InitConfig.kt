package com.kurban.xuehuaim.sdk.config

import com.kurban.xuehuaim.sdk.enum.IMPlatform
import kotlinx.serialization.Serializable

@Serializable
data class InitConfig(
    val platformId: Int? = null,
    val apiAddr: String,
    val wsAddr: String,
    val authAddr: String = "",
    val adminAddr: String? = null,
    val dbPath: String? = null,
    val dbName: String? = null,
    val logLevel: LogLevel = LogLevel.ALL,
) {
    fun resolvedPlatformId(): Int = platformId ?: IMPlatform.WEB.value
}

enum class LogLevel {
    NONE,
    ERROR,
    WARN,
    INFO,
    DEBUG,
    ALL,
}
