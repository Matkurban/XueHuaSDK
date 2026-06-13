package com.kurban.xuehuaim.sdk.db

import com.kurban.xuehuaim.sdk.model.ConversationInfo
import com.kurban.xuehuaim.sdk.model.Message
import com.kurban.xuehuaim.sdk.model.UserInfo
import com.kurban.xuehuaim.sdk.platform.DatabaseDriverFactory
import com.kurban.xuehuaim.sdk.util.mergeConversation
import com.kurban.xuehuaim.sdk.util.normalizedForStorage
import com.kurban.xuehuaim.sdk.util.withParsedContent
import com.kurban.xuehuaim.sdk.util.withParsedLatestMsg

class DatabaseService(
    driverFactory: DatabaseDriverFactory,
    dbPath: String,
) {
    private val database: ImDatabase = createImDatabase(driverFactory, dbPath)

    suspend fun switchSpace(userId: String) = database.switchSpace(userId)
    suspend fun insertOrReplaceUser(user: UserInfo) = database.insertOrReplaceUser(user)
    suspend fun getAllUsers(): List<UserInfo> = database.getAllUsers()
    suspend fun insertOrReplaceConversation(conversation: ConversationInfo) {
        val incoming = conversation.withParsedLatestMsg().normalizedForStorage()
        val existing = database.getConversation(incoming.conversationID)
        val merged = if (existing != null) mergeConversation(existing, incoming) else incoming
        database.insertOrReplaceConversation(merged.normalizedForStorage())
    }

    suspend fun getAllConversations(): List<ConversationInfo> =
        database.getAllConversations().map { it.withParsedLatestMsg() }

    suspend fun getVisibleConversations(): List<ConversationInfo> =
        database.getVisibleConversations().map { it.withParsedLatestMsg() }

    suspend fun getConversationsPage(offset: Int, count: Int): List<ConversationInfo> =
        database.getConversationsPage(offset, count).map { it.withParsedLatestMsg() }

    suspend fun getConversation(conversationId: String): ConversationInfo? =
        database.getConversation(conversationId)?.withParsedLatestMsg()

    suspend fun resetConversation(conversationId: String) =
        database.resetConversation(conversationId)

    suspend fun deleteConversation(conversationId: String) =
        database.deleteConversation(conversationId)

    suspend fun updateConversationUnread(
        conversationId: String,
        unreadCount: Int,
        hasReadSeq: Long
    ) =
        database.updateConversationUnread(conversationId, unreadCount, hasReadSeq)

    suspend fun getTotalUnreadCount(): Int = database.getTotalUnreadCount()
    suspend fun deleteChatLogsByConversation(conversationId: String) =
        database.deleteChatLogsByConversation(conversationId)

    suspend fun insertOrReplaceMessage(message: Message) =
        database.insertOrReplaceMessage(message.withParsedContent())

    suspend fun getMessages(conversationId: String, count: Long): List<Message> =
        database.getMessages(conversationId, count).map { it.withParsedContent() }

    suspend fun deleteMessage(clientMsgId: String) = database.deleteMessage(clientMsgId)
    suspend fun getVersionSync(tableName: String, entityId: String): VersionSyncInfo? =
        database.getVersionSync(tableName, entityId)

    suspend fun setVersionSync(
        tableName: String,
        entityId: String,
        versionID: String,
        version: Int
    ) =
        database.setVersionSync(tableName, entityId, versionID, version)

    suspend fun deleteVersionSync(tableName: String, entityId: String) =
        database.deleteVersionSync(tableName, entityId)
}
