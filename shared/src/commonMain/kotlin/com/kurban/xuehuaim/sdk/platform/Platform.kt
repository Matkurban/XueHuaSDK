package com.kurban.xuehuaim.sdk.platform

import com.kurban.xuehuaim.sdk.enum.IMPlatform
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

expect val sdkScope: CoroutineScope

expect val ioDispatcher: CoroutineDispatcher

expect fun currentPlatform(): IMPlatform

expect fun defaultDbPath(dbName: String = "openim.db"): String

expect class GzipCodec {
    fun compress(data: ByteArray): ByteArray
    fun decompress(data: ByteArray): ByteArray
    val isSupported: Boolean
}

expect class FileSystem {
    fun readBytes(path: String): ByteArray

    fun readBytes(path: String, offset: Long, length: Int): ByteArray

    fun fileSize(path: String): Long

    fun writeBytes(path: String, data: ByteArray)

    fun exists(path: String): Boolean

    fun md5(data: ByteArray): String

    fun md5File(path: String): String
}

expect class DatabaseDriverFactory {
    fun createDriver(dbPath: String): app.cash.sqldelight.db.SqlDriver
    suspend fun initializeSchema(driver: app.cash.sqldelight.db.SqlDriver)
}

expect fun createGzipCodec(): GzipCodec
expect fun createFileSystem(): FileSystem
expect fun createDatabaseDriverFactory(): DatabaseDriverFactory
