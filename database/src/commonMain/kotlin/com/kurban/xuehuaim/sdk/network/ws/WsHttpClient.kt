package com.kurban.xuehuaim.sdk.network.ws

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets

internal expect fun createWsHttpClient(): HttpClient

internal fun createWsHttpClientBase(engine: HttpClient): HttpClient = engine.config {
    install(WebSockets)
}
