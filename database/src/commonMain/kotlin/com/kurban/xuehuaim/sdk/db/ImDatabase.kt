package com.kurban.xuehuaim.sdk.db

import com.kurban.xuehuaim.sdk.model.ConversationInfo
import com.kurban.xuehuaim.sdk.model.Message
import com.kurban.xuehuaim.sdk.model.UserInfo
import com.kurban.xuehuaim.sdk.platform.DatabaseDriverFactory

internal interface ImDatabase {
    suspend fun switchSpace(userId: String)
    suspend fun insertOrReplaceUser(user: UserInfo)
    suspend fun getAllUsers(): List<UserInfo>
    suspend fun insertOrReplaceConversation(conversation: ConversationInfo)
    suspend fun getAllConversations(): List<ConversationInfo>
    suspend fun getVisibleConversations(): List<ConversationInfo>
    suspend fun getConversationsPage(offset: Int, count: Int): List<ConversationInfo>
    suspend fun getConversation(conversationId: String): ConversationInfo?
    suspend fun resetConversation(conversationId: String)
    suspend fun deleteConversation(conversationId: String)
    suspend fun updateConversationUnread(conversationId: String, unreadCount: Int, hasReadSeq: Long)
    suspend fun getTotalUnreadCount(): Int
    suspend fun deleteChatLogsByConversation(conversationId: String)
    suspend fun insertOrReplaceMessage(message: Message)
    suspend fun getMessages(conversationId: String, count: Long): List<Message>
    suspend fun deleteMessage(clientMsgId: String)
    suspend fun getVersionSync(tableName: String, entityId: String): VersionSyncInfo?
    suspend fun setVersionSync(tableName: String, entityId: String, versionID: String, version: Int)
    suspend fun deleteVersionSync(tableName: String, entityId: String)
}

internal expect fun createImDatabase(
    driverFactory: DatabaseDriverFactory,
    dbPath: String
): ImDatabase
