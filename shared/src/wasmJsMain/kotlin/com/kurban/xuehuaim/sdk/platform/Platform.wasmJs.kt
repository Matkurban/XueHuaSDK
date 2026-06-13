package com.kurban.xuehuaim.sdk.platform

import app.cash.sqldelight.db.SqlDriver
import com.kurban.xuehuaim.sdk.enum.IMPlatform
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okio.ByteString.Companion.toByteString

actual val sdkScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default

actual fun currentPlatform(): IMPlatform = IMPlatform.WEB

actual fun defaultDbPath(dbName: String): String = "xuehuaim-$dbName"

actual class GzipCodec {
    actual val isSupported: Boolean = false
    actual fun compress(data: ByteArray): ByteArray = data
    actual fun decompress(data: ByteArray): ByteArray = data
}

actual class FileSystem {
    private val storage = mutableMapOf<String, ByteArray>()

    actual fun readBytes(path: String): ByteArray = storage[path] ?: ByteArray(0)
    actual fun writeBytes(path: String, data: ByteArray) {
        storage[path] = data
    }

    actual fun exists(path: String): Boolean = storage.containsKey(path)
    actual fun md5(data: ByteArray): String = data.toByteString().md5().hex()
    actual fun md5File(path: String): String = md5(readBytes(path))
}

actual class DatabaseDriverFactory {
    actual fun createDriver(dbPath: String): SqlDriver =
        error("Wasm uses InMemoryImDatabase")

    actual suspend fun initializeSchema(driver: SqlDriver) = Unit
}

actual fun createGzipCodec(): GzipCodec = GzipCodec()
actual fun createFileSystem(): FileSystem = FileSystem()
actual fun createDatabaseDriverFactory(): DatabaseDriverFactory = DatabaseDriverFactory()
