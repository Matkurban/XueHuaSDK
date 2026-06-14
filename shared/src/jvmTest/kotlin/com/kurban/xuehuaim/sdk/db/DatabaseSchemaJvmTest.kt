package com.kurban.xuehuaim.sdk.db

import com.kurban.xuehuaim.sdk.platform.createDatabaseDriverFactory
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class DatabaseSchemaJvmTest {
    @Test
    fun reopenExistingDatabaseDoesNotFail() = runBlocking {
        val dbDir = File(
            System.getProperty("java.io.tmpdir"),
            "xuehuaim-db-test-${System.nanoTime()}"
        ).apply { mkdirs() }
        val dbPath = File(dbDir, "user-1.db").absolutePath
        val driverFactory = createDatabaseDriverFactory()

        createImDatabase(driverFactory, dbPath).use { first ->
            first.insertOrReplaceUser(
                com.kurban.xuehuaim.sdk.model.UserInfo(userID = "user-1", nickname = "test"),
            )
        }
        createImDatabase(driverFactory, dbPath).use { second ->
            val users = second.getAllUsers()
            assertTrue(users.any { it.userID == "user-1" })
        }
    }

    private suspend fun ImDatabase.use(block: suspend (ImDatabase) -> Unit) {
        try {
            block(this)
        } finally {
            close()
        }
    }
}
