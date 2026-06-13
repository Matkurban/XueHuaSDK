package com.kurban.xuehuaim.sdk.testutil

import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.network.createHttpEngine
import com.kurban.xuehuaim.sdk.network.ws.WebSocketService
import com.kurban.xuehuaim.sdk.platform.GzipCodec

internal fun createTestWebSocketService(
    eventEmitter: SdkEventEmitter = SdkEventEmitter(),
): WebSocketService = WebSocketService(
    wsUrl = "ws://127.0.0.1:0",
    platformId = 1,
    gzipCodec = GzipCodec(),
    eventEmitter = eventEmitter,
    httpClient = createHttpEngine(),
)
