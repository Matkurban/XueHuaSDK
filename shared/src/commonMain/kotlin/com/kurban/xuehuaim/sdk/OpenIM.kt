package com.kurban.xuehuaim.sdk

import com.kurban.xuehuaim.sdk.config.InitConfig
import com.kurban.xuehuaim.sdk.di.SdkModule
import com.kurban.xuehuaim.sdk.manager.IMManager
import org.koin.core.context.startKoin

object OpenIM {
    const val AT_ALL_TAG = "AtAllTag"

    private var koinStarted = false

    val iMManager: IMManager
        get() = IMManager.instance

    val isInitialized: Boolean
        get() = runCatching { iMManager.isInitialized }.getOrDefault(false)

    fun initialize(config: InitConfig? = null) {
        if (!koinStarted) {
            startKoin { modules(SdkModule.create(config)) }
            koinStarted = true
        }
    }
}
