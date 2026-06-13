package com.kurban.xuehuaim.sdk.util

import kotlin.random.Random

object OperationIdGenerator {
    fun generate(): String = System.currentTimeMillis().toString()
}

object ClientMsgIdGenerator {
    fun generate(): String = buildString {
        append(System.currentTimeMillis())
        append(Random.nextInt(1000, 9999))
    }
}

internal expect object System {
    fun currentTimeMillis(): Long
}
