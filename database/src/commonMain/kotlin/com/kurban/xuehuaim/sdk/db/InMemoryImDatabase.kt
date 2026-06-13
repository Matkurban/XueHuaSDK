package com.kurban.xuehuaim.sdk.db

import com.kurban.xuehuaim.sdk.model.ConversationInfo
import com.kurban.xuehuaim.sdk.model.Message
import com.kurban.xuehuaim.sdk.model.UserInfo
import com.kurban.xuehuaim.sdk.platform.ioDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class InMemoryImDatabase : ImDatabase {
    private val mutex = Mutex()
    private val users = mutableListOf<UserInfo>()
    private val conversations = mutableListOf<ConversationInfo>()
    private val messages = mutableListOf<Message>()
    private val versionSync = mutableMapOf<String, VersionSyncInfo>()

    override suspend fun switchSpace(userId: String) = withContext(ioDispatcher) {
        mutex.withLock {
            users.clear()
            conversations.clear()
            messages.clear()
            versionSync.clear()
        }
    }

    override suspend fun insertOrReplaceUser(user: UserInfo) = withContext(ioDispatcher) {
        mutex.withLock {
            users.removeAll { it.userID == user.userID }
            users.add(user)
            Unit
        }
    }

    override suspend fun getAllUsers(): List<UserInfo> = withContext(ioDispatcher) {
        mutex.withLock { users.toList() }
    }

    override suspend fun insertOrReplaceConversation(conversation: ConversationInfo) =
        withContext(ioDispatcher) {
            mutex.withLock {
                conversations.removeAll { it.conversationID == conversation.conversationID }
                conversations.add(conversation)
                Unit
            }
        }

    override suspend fun getAllConversations(): List<ConversationInfo> = withContext(ioDispatcher) {
        mutex.withLock { conversations.sortedByDescending { it.latestMsgSendTime ?: 0 }.toList() }
    }

    override suspend fun getVisibleConversations(): List<ConversationInfo> =
        withContext(ioDispatcher) {
            mutex.withLock {
                conversations.filter { (it.latestMsgSendTime ?: 0) > 0 }
                    .sortedByDescending { it.latestMsgSendTime ?: 0 }
                    .toList()
            }
        }

    override suspend fun getConversationsPage(offset: Int, count: Int): List<ConversationInfo> =
        withContext(ioDispatcher) {
            mutex.withLock {
                conversations.filter { (it.latestMsgSendTime ?: 0) > 0 }
                    .sortedByDescending { it.latestMsgSendTime ?: 0 }
                    .drop(offset)
                    .take(count)
            }
        }

    override suspend fun getConversation(conversationId: String): ConversationInfo? =
        withContext(ioDispatcher) {
            mutex.withLock { conversations.find { it.conversationID == conversationId } }
        }

    override suspend fun resetConversation(conversationId: String) = withContext(ioDispatcher) {
        mutex.withLock {
            val index = conversations.indexOfFirst { it.conversationID == conversationId }
            if (index >= 0) {
                conversations[index] = conversations[index].copy(
                    unreadCount = 0,
                    latestMsg = null,
                    latestMsgSendTime = 0,
                    draftText = "",
                    draftTextTime = 0,
                )
            }
            Unit
        }
    }

    override suspend fun deleteConversation(conversationId: String) = withContext(ioDispatcher) {
        mutex.withLock {
            conversations.removeAll { it.conversationID == conversationId }
            Unit
        }
    }

    override suspend fun updateConversationUnread(
        conversationId: String,
        unreadCount: Int,
        hasReadSeq: Long,
    ) = withContext(ioDispatcher) {
        mutex.withLock {
            val index = conversations.indexOfFirst { it.conversationID == conversationId }
            if (index >= 0) {
                conversations[index] = conversations[index].copy(
                    unreadCount = unreadCount,
                    hasReadSeq = hasReadSeq,
                )
            }
            Unit
        }
    }

    override suspend fun getTotalUnreadCount(): Int = withContext(ioDispatcher) {
        mutex.withLock {
            conversations.filter { (it.latestMsgSendTime ?: 0) > 0 }.sumOf { it.unreadCount }
        }
    }

    override suspend fun deleteChatLogsByConversation(conversationId: String) =
        withContext(ioDispatcher) {
            mutex.withLock {
                messages.removeAll { it.conversationID == conversationId }
                Unit
            }
        }

    override suspend fun insertOrReplaceMessage(message: Message) = withContext(ioDispatcher) {
        mutex.withLock {
            messages.removeAll { it.clientMsgID == message.clientMsgID }
            messages.add(message)
            Unit
        }
    }

    override suspend fun getMessages(conversationId: String, count: Long): List<Message> =
        withContext(ioDispatcher) {
            mutex.withLock {
                messages.filter { it.conversationID == conversationId }
                    .sortedByDescending { it.sendTime ?: 0 }
                    .take(count.toInt())
            }
        }

    override suspend fun deleteMessage(clientMsgId: String) = withContext(ioDispatcher) {
        mutex.withLock {
            messages.removeAll { it.clientMsgID == clientMsgId }
            Unit
        }
    }

    override suspend fun getVersionSync(tableName: String, entityId: String): VersionSyncInfo? =
        withContext(ioDispatcher) {
            mutex.withLock { versionSync["$tableName|$entityId"] }
        }

    override suspend fun setVersionSync(
        tableName: String,
        entityId: String,
        versionID: String,
        version: Int,
    ) = withContext(ioDispatcher) {
        mutex.withLock {
            versionSync["$tableName|$entityId"] =
                VersionSyncInfo(versionID = versionID, version = version)
            Unit
        }
    }

    override suspend fun deleteVersionSync(tableName: String, entityId: String) =
        withContext(ioDispatcher) {
            mutex.withLock {
                versionSync.remove("$tableName|$entityId")
                Unit
            }
        }
}
