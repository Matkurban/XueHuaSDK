package com.kurban.xuehuaim.sdk.platform

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.kurban.xuehuaim.sdk.db.OpenIMDatabase
import com.kurban.xuehuaim.sdk.enum.IMPlatform
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory

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

actual class FileSystem {
    actual fun readBytes(path: String): ByteArray = ByteArray(0)
    actual fun writeBytes(path: String, data: ByteArray) = Unit
    actual fun exists(path: String): Boolean = NSFileManager.defaultManager.fileExistsAtPath(path)
    actual fun md5(data: ByteArray): String = data.contentHashCode().toString()
    actual fun md5File(path: String): String = path.hashCode().toString()
}

actual class DatabaseDriverFactory {
    actual fun createDriver(dbPath: String): SqlDriver =
        NativeSqliteDriver(OpenIMDatabase.Schema, dbPath.substringAfterLast('/'))

    actual suspend fun initializeSchema(driver: SqlDriver) = Unit
}

actual fun createGzipCodec(): GzipCodec = GzipCodec()
actual fun createFileSystem(): FileSystem = FileSystem()
actual fun createDatabaseDriverFactory(): DatabaseDriverFactory = DatabaseDriverFactory()
