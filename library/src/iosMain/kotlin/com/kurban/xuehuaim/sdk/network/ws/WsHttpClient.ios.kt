package com.kurban.xuehuaim.sdk.network.ws

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

actual fun createWsHttpClient(): HttpClient = createWsHttpClientBase(HttpClient(Darwin))
