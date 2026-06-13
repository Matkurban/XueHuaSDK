package com.kurban.xuehuaim.sdk.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

actual fun createHttpEngine(): HttpClient = HttpClient(OkHttp)
