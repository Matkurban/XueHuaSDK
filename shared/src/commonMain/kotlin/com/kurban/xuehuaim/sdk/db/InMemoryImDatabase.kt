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
    private val grabbedRedPackets = mutableMapOf<String, Long>()
    private val kvStore = mutableMapOf<Pair<String, Boolean>, String?>()
    private val sendingMessages = mutableListOf<SendingMessage>()

    override suspend fun close() = Unit

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

    override suspend fun insertOrReplaceGrabbedRedPacket(packetId: String, grabTime: Long) =
        withContext(ioDispatcher) {
            mutex.withLock {
                grabbedRedPackets[packetId] = grabTime
                Unit
            }
        }

    override suspend fun selectGrabbedRedPacket(packetId: String): Long? = withContext(ioDispatcher) {
        mutex.withLock { grabbedRedPackets[packetId] }
    }

    override suspend fun selectGrabbedRedPacketIds(packetIds: List<String>): List<String> =
        withContext(ioDispatcher) {
            mutex.withLock { packetIds.filter { grabbedRedPackets.containsKey(it) } }
        }

    override suspend fun insertOrReplaceKv(key: String, value: String?, isGlobal: Boolean) =
        withContext(ioDispatcher) {
            mutex.withLock {
                kvStore[key to isGlobal] = value
                Unit
            }
        }

    override suspend fun selectKv(key: String, isGlobal: Boolean): String? =
        withContext(ioDispatcher) {
            mutex.withLock { kvStore[key to isGlobal] }
        }

    override suspend fun deleteKv(key: String, isGlobal: Boolean) = withContext(ioDispatcher) {
        mutex.withLock {
            kvStore.remove(key to isGlobal)
            Unit
        }
    }

    override suspend fun selectMessageByClientMsgId(clientMsgId: String): Message? =
        withContext(ioDispatcher) {
            mutex.withLock { messages.find { it.clientMsgID == clientMsgId } }
        }

    override suspend fun updateChatLogContent(clientMsgId: String, content: String) =
        withContext(ioDispatcher) {
            mutex.withLock {
                val index = messages.indexOfFirst { it.clientMsgID == clientMsgId }
                if (index >= 0) {
                    messages[index] = messages[index].copy(content = content)
                }
                Unit
            }
        }

    override suspend fun updateMessageContentType(clientMsgId: String, contentType: Int) =
        withContext(ioDispatcher) {
            mutex.withLock {
                val index = messages.indexOfFirst { it.clientMsgID == clientMsgId }
                if (index >= 0) {
                    val type = com.kurban.xuehuaim.sdk.enum.MessageType.fromValue(contentType)
                    messages[index] = messages[index].copy(contentType = type)
                }
                Unit
            }
        }

    override suspend fun updateMessageLocalEx(clientMsgId: String, localEx: String) =
        withContext(ioDispatcher) {
            mutex.withLock {
                val index = messages.indexOfFirst { it.clientMsgID == clientMsgId }
                if (index >= 0) {
                    messages[index] = messages[index].copy(localEx = localEx)
                }
                Unit
            }
        }

    override suspend fun markMessageAsRead(clientMsgId: String) = withContext(ioDispatcher) {
        mutex.withLock {
            val index = messages.indexOfFirst { it.clientMsgID == clientMsgId }
            if (index >= 0) {
                messages[index] = messages[index].copy(isRead = true)
            }
            Unit
        }
    }

    override suspend fun selectAllMessages(): List<Message> = withContext(ioDispatcher) {
        mutex.withLock { messages.sortedByDescending { it.sendTime ?: 0 }.toList() }
    }

    override suspend fun selectSendingMessages(): List<SendingMessage> = withContext(ioDispatcher) {
        mutex.withLock { sendingMessages.toList() }
    }

    override suspend fun deleteAllChatLogs() = withContext(ioDispatcher) {
        mutex.withLock {
            messages.clear()
            Unit
        }
    }

    override suspend fun hideAllConversations() = withContext(ioDispatcher) {
        mutex.withLock {
            conversations.indices.forEach { index ->
                conversations[index] = conversations[index].copy(latestMsgSendTime = 0)
            }
            Unit
        }
    }
}
