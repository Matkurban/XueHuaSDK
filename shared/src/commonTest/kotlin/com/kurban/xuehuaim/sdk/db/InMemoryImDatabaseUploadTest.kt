package com.kurban.xuehuaim.sdk.db

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InMemoryImDatabaseUploadTest {
    private val database = InMemoryImDatabase()

    @Test
    fun upload_lookupByHashAndName() = runBlocking {
        val record = UploadRecord(
            uploadID = "upload-1",
            hash = "abc123",
            name = "user1/msg_picture_id.jpg",
            fileSize = 1024,
            partSize = 512,
            partNum = 2,
            uploadedParts = UploadPartsCodec.encode(listOf(1)),
            updateTime = 1L,
        )
        database.insertOrReplaceUpload(record)

        val byId = database.getUpload("upload-1")
        assertEquals("upload-1", byId?.uploadID)

        val byHash = database.getUploadByHashAndName("abc123", "user1/msg_picture_id.jpg")
        assertEquals(listOf(1), UploadPartsCodec.decode(byHash?.uploadedParts))

        database.deleteUpload("upload-1")
        assertNull(database.getUpload("upload-1"))
        assertNull(database.getUploadByHashAndName("abc123", "user1/msg_picture_id.jpg"))
    }
}
