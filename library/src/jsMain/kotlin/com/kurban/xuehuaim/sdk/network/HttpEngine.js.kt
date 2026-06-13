package com.kurban.xuehuaim.sdk.network.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js

actual fun createHttpEngine(): HttpClient = HttpClient(Js)
