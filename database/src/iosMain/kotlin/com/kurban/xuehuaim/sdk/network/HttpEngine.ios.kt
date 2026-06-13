package com.kurban.xuehuaim.sdk.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

actual fun createHttpEngine(): HttpClient = HttpClient(Darwin)
