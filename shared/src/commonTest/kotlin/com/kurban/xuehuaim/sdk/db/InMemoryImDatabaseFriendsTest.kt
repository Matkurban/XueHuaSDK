package com.kurban.xuehuaim.sdk.db

import com.kurban.xuehuaim.sdk.model.BlacklistInfo
import com.kurban.xuehuaim.sdk.model.FriendInfo
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InMemoryImDatabaseFriendsTest {
    private val database = InMemoryImDatabase()

    @Test
    fun friends_insertAndQuery() = runBlocking {
        val friend = FriendInfo(
            ownerUserID = "self",
            userID = "u1",
            nickname = "Alice",
            faceURL = "https://example.com/a.png",
        )
        database.insertOrReplaceFriend(friend)
        val all = database.getAllFriends()
        assertEquals(1, all.size)
        assertEquals("u1", all.first().userID)
    }

    @Test
    fun friends_blacklistExclusion() = runBlocking {
        database.batchUpsertFriends(
            listOf(
                FriendInfo(ownerUserID = "self", userID = "u1", nickname = "Alice"),
                FriendInfo(ownerUserID = "self", userID = "u2", nickname = "Bob"),
            ),
        )
        database.insertOrReplaceBlack(
            BlacklistInfo(ownerUserID = "self", blockUserID = "u2"),
        )
        val blackIds = database.getBlackUserIds()
        val visible = database.getAllFriends().filterNot { it.userID in blackIds }
        assertEquals(1, visible.size)
        assertEquals("u1", visible.first().userID)
    }

    @Test
    fun sendingMessages_insertAndDelete() = runBlocking {
        database.insertOrReplaceSendingMessage(
            SendingMessage(clientMsgID = "m1", conversationID = "c1"),
        )
        assertEquals(1, database.selectSendingMessages().size)
        database.deleteSendingMessage("m1")
        assertTrue(database.selectSendingMessages().isEmpty())
    }
}
