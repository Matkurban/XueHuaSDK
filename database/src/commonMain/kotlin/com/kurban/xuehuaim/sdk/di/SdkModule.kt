package com.kurban.xuehuaim.sdk.di

import com.kurban.xuehuaim.sdk.config.InitConfig
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.manager.IMManager
import com.kurban.xuehuaim.sdk.platform.createDatabaseDriverFactory
import com.kurban.xuehuaim.sdk.platform.createFileSystem
import com.kurban.xuehuaim.sdk.platform.createGzipCodec
import org.koin.dsl.module

internal object SdkModule {
    fun create(config: InitConfig? = null) = module {
        single { SdkEventEmitter() }
        single { createDatabaseDriverFactory() }
        single { createFileSystem() }
        single { createGzipCodec() }
        single {
            IMManager.create(
                eventEmitter = get(),
                driverFactory = get(),
                fileSystem = get(),
                gzipCodec = get(),
                config = config,
            )
        }
    }
}
