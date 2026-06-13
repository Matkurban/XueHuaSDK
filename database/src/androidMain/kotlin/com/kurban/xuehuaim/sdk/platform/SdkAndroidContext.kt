package com.kurban.xuehuaim.sdk.platform

import android.content.Context

object SdkAndroidContext {
    lateinit var applicationContext: Context

    fun init(context: Context) {
        applicationContext = context.applicationContext
    }
}
