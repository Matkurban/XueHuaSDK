package com.kurban.xuehuaim.sdk.network.ws

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

actual fun createWsHttpClient(): HttpClient = createWsHttpClientBase(HttpClient(CIO))
