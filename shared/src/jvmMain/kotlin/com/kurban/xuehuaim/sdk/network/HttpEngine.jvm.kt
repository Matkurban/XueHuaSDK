package com.kurban.xuehuaim.sdk.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

actual fun createHttpEngine(): HttpClient = HttpClient(CIO)
