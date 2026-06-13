package com.kurban.xuehuaim.sdk.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object MessageSyncMutex {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}
