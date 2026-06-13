package com.kurban.xuehuaim.sdk.platform

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.kurban.xuehuaim.sdk.db.OpenIMDatabase
import com.kurban.xuehuaim.sdk.enum.IMPlatform
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import okio.ByteString.Companion.toByteString
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

actual val sdkScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

actual fun currentPlatform(): IMPlatform = IMPlatform.IOS

actual fun defaultDbPath(dbName: String): String =
    "${NSHomeDirectory()}/Documents/$dbName"

actual class GzipCodec {
    actual val isSupported: Boolean = true
    actual fun compress(data: ByteArray): ByteArray = data
    actual fun decompress(data: ByteArray): ByteArray = data
}

@OptIn(ExperimentalForeignApi::class)
actual class FileSystem {
    actual fun readBytes(path: String): ByteArray {
        val data = NSData.dataWithContentsOfFile(path) ?: return ByteArray(0)
        return data.toByteArray()
    }

    actual fun writeBytes(path: String, data: ByteArray) {
        val parent = path.substringBeforeLast('/', missingDelimiterValue = "")
        if (parent.isNotEmpty()) {
            NSFileManager.defaultManager.createDirectoryAtPath(
                path = parent,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
        }
        NSFileManager.defaultManager.createFileAtPath(
            path = path,
            contents = data.toNSData(),
            attributes = null,
        )
    }

    actual fun exists(path: String): Boolean = NSFileManager.defaultManager.fileExistsAtPath(path)
    actual fun md5(data: ByteArray): String = data.toByteString().md5().hex()
    actual fun md5File(path: String): String = md5(readBytes(path))
}

actual class DatabaseDriverFactory {
    actual fun createDriver(dbPath: String): SqlDriver =
        NativeSqliteDriver(OpenIMDatabase.Schema, dbPath.substringAfterLast('/'))

    actual suspend fun initializeSchema(driver: SqlDriver) = Unit
}

actual fun createGzipCodec(): GzipCodec = GzipCodec()
actual fun createFileSystem(): FileSystem = FileSystem()
actual fun createDatabaseDriverFactory(): DatabaseDriverFactory = DatabaseDriverFactory()

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    if (length == 0) return ByteArray(0)
    return ByteArray(length).apply {
        usePinned { pinned ->
            memcpy(pinned.addressOf(0), bytes, this@toByteArray.length)
        }
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
}
