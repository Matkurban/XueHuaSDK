package com.kurban.xuehuaim.sdk.db

import com.kurban.xuehuaim.sdk.model.ConversationInfo
import com.kurban.xuehuaim.sdk.model.Message
import com.kurban.xuehuaim.sdk.model.UserInfo
import com.kurban.xuehuaim.sdk.platform.DatabaseDriverFactory

internal interface ImDatabase {
    suspend fun close()
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
    suspend fun insertOrReplaceGrabbedRedPacket(packetId: String, grabTime: Long)
    suspend fun selectGrabbedRedPacket(packetId: String): Long?
    suspend fun selectGrabbedRedPacketIds(packetIds: List<String>): List<String>
    suspend fun insertOrReplaceKv(key: String, value: String?, isGlobal: Boolean)
    suspend fun selectKv(key: String, isGlobal: Boolean): String?
    suspend fun deleteKv(key: String, isGlobal: Boolean)
    suspend fun selectMessageByClientMsgId(clientMsgId: String): Message?
    suspend fun updateChatLogContent(clientMsgId: String, content: String)
    suspend fun updateMessageContentType(clientMsgId: String, contentType: Int)
    suspend fun updateMessageLocalEx(clientMsgId: String, localEx: String)
    suspend fun markMessageAsRead(clientMsgId: String)
    suspend fun selectAllMessages(): List<Message>
    suspend fun selectSendingMessages(): List<SendingMessage>
    suspend fun deleteAllChatLogs()
    suspend fun hideAllConversations()
}

internal expect suspend fun createImDatabase(
    driverFactory: DatabaseDriverFactory,
    dbPath: String,
): ImDatabase
