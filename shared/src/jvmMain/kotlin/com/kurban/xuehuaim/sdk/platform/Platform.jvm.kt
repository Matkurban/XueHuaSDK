package com.kurban.xuehuaim.sdk.platform

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kurban.xuehuaim.sdk.db.OpenIMDatabase
import com.kurban.xuehuaim.sdk.enum.IMPlatform
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

actual val sdkScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

actual fun currentPlatform(): IMPlatform = IMPlatform.LINUX

actual fun defaultDbPath(dbName: String): String {
    val home = System.getProperty("user.home") ?: "."
    return File(home, ".xuehuaim/$dbName").absolutePath
}

actual class GzipCodec {
    actual val isSupported: Boolean = true

    actual fun compress(data: ByteArray): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(data) }
        return output.toByteArray()
    }

    actual fun decompress(data: ByteArray): ByteArray =
        GZIPInputStream(data.inputStream()).use { it.readBytes() }
}

actual class FileSystem {
    actual fun readBytes(path: String): ByteArray = File(path).readBytes()

    actual fun readBytes(path: String, offset: Long, length: Int): ByteArray {
        File(path).inputStream().use { input ->
            if (offset > 0) {
                input.skip(offset)
            }
            val buffer = ByteArray(length)
            var read = 0
            while (read < length) {
                val count = input.read(buffer, read, length - read)
                if (count <= 0) break
                read += count
            }
            return if (read == length) buffer else buffer.copyOf(read)
        }
    }

    actual fun fileSize(path: String): Long = File(path).length()

    actual fun writeBytes(path: String, data: ByteArray) {
        File(path).parentFile?.mkdirs()
        File(path).writeBytes(data)
    }

    actual fun exists(path: String): Boolean = File(path).exists()
    actual fun md5(data: ByteArray): String = MessageDigest.getInstance("MD5").digest(data).toHex()
    actual fun md5File(path: String): String = md5(readBytes(path))

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

actual class DatabaseDriverFactory {
    actual fun createDriver(dbPath: String): SqlDriver {
        val file = File(dbPath)
        file.parentFile?.mkdirs()
        return JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
    }

    actual suspend fun initializeSchema(driver: SqlDriver) {
        OpenIMDatabase.Schema.create(driver)
    }
}

actual fun createGzipCodec(): GzipCodec = GzipCodec()
actual fun createFileSystem(): FileSystem = FileSystem()
actual fun createDatabaseDriverFactory(): DatabaseDriverFactory = DatabaseDriverFactory()
