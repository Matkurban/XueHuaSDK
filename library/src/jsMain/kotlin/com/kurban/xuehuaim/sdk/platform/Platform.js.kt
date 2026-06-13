package com.kurban.xuehuaim.sdk.platform

import com.kurban.xuehuaim.sdk.enum.IMPlatform
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
    actual fun readBytes(path: String): ByteArray = ByteArray(0)
    actual fun writeBytes(path: String, data: ByteArray) = Unit
    actual fun exists(path: String): Boolean = false
    actual fun md5(data: ByteArray): String = data.size.toString()
    actual fun md5File(path: String): String = path.hashCode().toString()
}

actual class DatabaseDriverFactory {
    actual fun createDriver(dbPath: String): app.cash.sqldelight.db.SqlDriver =
        error("JS uses InMemoryImDatabase")
}

actual fun createGzipCodec(): GzipCodec = GzipCodec()
actual fun createFileSystem(): FileSystem = FileSystem()
actual fun createDatabaseDriverFactory(): DatabaseDriverFactory = DatabaseDriverFactory()
