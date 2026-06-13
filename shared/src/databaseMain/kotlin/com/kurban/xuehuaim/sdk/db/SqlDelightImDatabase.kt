package com.kurban.xuehuaim.sdk.db

import app.cash.sqldelight.db.SqlDriver
import com.kurban.xuehuaim.sdk.model.ConversationInfo
import com.kurban.xuehuaim.sdk.model.Message
import com.kurban.xuehuaim.sdk.model.UserInfo
import com.kurban.xuehuaim.sdk.platform.ioDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

internal class SqlDelightImDatabase(
    private val driver: SqlDriver,
    database: OpenIMDatabase,
) : ImDatabase {
    private val queries = database.openIMDatabaseQueries
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun close() = withContext(ioDispatcher) {
        driver.close()
    }

    override suspend fun insertOrReplaceUser(user: UserInfo) = withContext(ioDispatcher) {
        queries.insertOrReplaceUser(
            Local_users(
                userID = user.userID,
                nickname = user.nickname,
                faceURL = user.faceURL,
                ex = user.ex,
                createTime = user.createTime,
                appMangerLevel = user.appMangerLevel?.toLong(),
                globalRecvMsgOpt = user.globalRecvMsgOpt?.value?.toLong(),
            ),
        )
        Unit
    }

    override suspend fun getAllUsers(): List<UserInfo> = withContext(ioDispatcher) {
        queries.selectAllUsers().executeAsList().map { row ->
            UserInfo(
                userID = row.userID,
                nickname = row.nickname,
                faceURL = row.faceURL,
                ex = row.ex,
                createTime = row.createTime,
                appMangerLevel = row.appMangerLevel?.toInt(),
                globalRecvMsgOpt = row.globalRecvMsgOpt?.toInt()?.let { v ->
                    com.kurban.xuehuaim.sdk.enum.ReceiveMessageOpt.entries.find { it.value == v }
                },
            )
        }
    }

    override suspend fun insertOrReplaceConversation(conversation: ConversationInfo) =
        withContext(ioDispatcher) {
            queries.insertOrReplaceConversation(
                Local_conversations(
                    conversationID = conversation.conversationID,
                    conversationType = conversation.conversationType?.value?.toLong(),
                    userID = conversation.userID,
                    groupID = conversation.groupID,
                    showName = conversation.showName,
                    faceURL = conversation.faceURL,
                    recvMsgOpt = conversation.recvMsgOpt?.value?.toLong(),
                    unreadCount = conversation.unreadCount.toLong(),
                    latestMsg = conversation.latestMsg?.let { json.encodeToString(it) },
                    latestMsgSendTime = conversation.latestMsgSendTime,
                    draftText = conversation.draftText,
                    draftTextTime = conversation.draftTextTime,
                    isPinned = if (conversation.isPinned == true) 1L else 0L,
                    isPrivateChat = if (conversation.isPrivateChat == true) 1L else 0L,
                    burnDuration = conversation.burnDuration?.toLong(),
                    isMsgDestruct = if (conversation.isMsgDestruct == true) 1L else 0L,
                    msgDestructTime = conversation.msgDestructTime,
                    ex = conversation.ex,
                    isNotInGroup = if (conversation.isNotInGroup == true) 1L else 0L,
                    groupAtType = conversation.groupAtType?.value?.toLong(),
                    maxSeq = conversation.maxSeq,
                    minSeq = conversation.minSeq,
                    hasReadSeq = conversation.hasReadSeq,
                ),
            )
            Unit
        }

    override suspend fun getAllConversations(): List<ConversationInfo> = withContext(ioDispatcher) {
        queries.selectAllConversations().executeAsList().map(ConversationDbMapper::fromRow)
    }

    override suspend fun getVisibleConversations(): List<ConversationInfo> =
        withContext(ioDispatcher) {
            queries.selectVisibleConversations().executeAsList().map(ConversationDbMapper::fromRow)
        }

    override suspend fun getConversationsPage(offset: Int, count: Int): List<ConversationInfo> =
        withContext(ioDispatcher) {
            queries.selectVisibleConversationsPage(count.toLong(), offset.toLong())
                .executeAsList()
                .map(ConversationDbMapper::fromRow)
        }

    override suspend fun getConversation(conversationId: String): ConversationInfo? =
        withContext(ioDispatcher) {
            queries.selectConversationById(conversationId).executeAsOneOrNull()
                ?.let(ConversationDbMapper::fromRow)
        }

    override suspend fun resetConversation(conversationId: String) = withContext(ioDispatcher) {
        queries.resetConversation(conversationId)
        Unit
    }

    override suspend fun deleteConversation(conversationId: String) = withContext(ioDispatcher) {
        queries.deleteConversation(conversationId)
        Unit
    }

    override suspend fun updateConversationUnread(
        conversationId: String,
        unreadCount: Int,
        hasReadSeq: Long,
    ) = withContext(ioDispatcher) {
        queries.updateConversationUnreadAndHasReadSeq(
            unreadCount = unreadCount.toLong(),
            hasReadSeq = hasReadSeq,
            conversationID = conversationId,
        )
        Unit
    }

    override suspend fun getTotalUnreadCount(): Int = withContext(ioDispatcher) {
        queries.selectTotalUnreadCount().executeAsOne().toInt()
    }

    override suspend fun deleteChatLogsByConversation(conversationId: String) =
        withContext(ioDispatcher) {
            queries.deleteChatLogsByConversation(conversationId)
            Unit
        }

    override suspend fun insertOrReplaceMessage(message: Message) = withContext(ioDispatcher) {
        queries.insertOrReplaceChatLog(message.toChatLogRow())
        Unit
    }

    override suspend fun getMessages(conversationId: String, count: Long): List<Message> =
        withContext(ioDispatcher) {
            queries.selectMessagesByConversation(conversationId, count)
                .executeAsList()
                .map(MessageDbMapper::fromRow)
        }

    override suspend fun deleteMessage(clientMsgId: String) = withContext(ioDispatcher) {
        queries.deleteChatLog(clientMsgId)
        Unit
    }

    override suspend fun getVersionSync(tableName: String, entityId: String): VersionSyncInfo? =
        withContext(ioDispatcher) {
            queries.selectVersionSync(tableName, entityId).executeAsOneOrNull()?.let { row ->
                VersionSyncInfo(
                    versionID = row.versionID.orEmpty(),
                    version = row.version?.toInt() ?: 0,
                )
            }
        }

    override suspend fun setVersionSync(
        tableName: String,
        entityId: String,
        versionID: String,
        version: Int,
    ) = withContext(ioDispatcher) {
        queries.insertOrReplaceVersionSync(
            Local_version_sync(
                id = "$tableName|$entityId",
                tableName = tableName,
                entityID = entityId,
                versionID = versionID,
                version = version.toLong(),
                uidList = null,
                updateTime = com.kurban.xuehuaim.sdk.util.System.currentTimeMillis(),
            ),
        )
        Unit
    }

    override suspend fun deleteVersionSync(tableName: String, entityId: String) =
        withContext(ioDispatcher) {
            queries.deleteVersionSync(tableName, entityId)
            Unit
        }

    override suspend fun insertOrReplaceGrabbedRedPacket(packetId: String, grabTime: Long) =
        withContext(ioDispatcher) {
            queries.insertOrReplaceGrabbedRedPacket(
                Local_grabbed_red_packets(packetID = packetId, grabTime = grabTime),
            )
            Unit
        }

    override suspend fun selectGrabbedRedPacket(packetId: String): Long? =
        withContext(ioDispatcher) {
            queries.selectGrabbedRedPacket(packetId).executeAsOneOrNull()
        }

    override suspend fun selectGrabbedRedPacketIds(packetIds: List<String>): List<String> =
        withContext(ioDispatcher) {
            if (packetIds.isEmpty()) return@withContext emptyList()
            queries.selectGrabbedRedPacketIds(packetIds).executeAsList()
        }

    override suspend fun insertOrReplaceKv(key: String, value: String?, isGlobal: Boolean) =
        withContext(ioDispatcher) {
            queries.insertOrReplaceKv(
                Local_kv_store(
                    key = key,
                    value_ = value,
                    isGlobal = if (isGlobal) 1L else 0L,
                ),
            )
            Unit
        }

    override suspend fun selectKv(key: String, isGlobal: Boolean): String? =
        withContext(ioDispatcher) {
            queries.selectKv(key, if (isGlobal) 1L else 0L).executeAsOneOrNull()?.value_
        }

    override suspend fun deleteKv(key: String, isGlobal: Boolean) = withContext(ioDispatcher) {
        queries.deleteKv(key, if (isGlobal) 1L else 0L)
        Unit
    }

    override suspend fun selectMessageByClientMsgId(clientMsgId: String): Message? =
        withContext(ioDispatcher) {
            queries.selectMessageByClientMsgId(clientMsgId).executeAsOneOrNull()
                ?.let(MessageDbMapper::fromRow)
        }

    override suspend fun updateChatLogContent(clientMsgId: String, content: String) =
        withContext(ioDispatcher) {
            queries.updateChatLogContent(content, clientMsgId)
            Unit
        }

    override suspend fun updateMessageContentType(clientMsgId: String, contentType: Int) =
        withContext(ioDispatcher) {
            queries.updateChatLogContentType(contentType.toLong(), clientMsgId)
            Unit
        }

    override suspend fun updateMessageLocalEx(clientMsgId: String, localEx: String) =
        withContext(ioDispatcher) {
            val row = queries.selectMessageByClientMsgId(clientMsgId).executeAsOneOrNull()
                ?: return@withContext
            queries.insertOrReplaceChatLog(row.copy(localEx = localEx))
            Unit
        }

    override suspend fun markMessageAsRead(clientMsgId: String) = withContext(ioDispatcher) {
        queries.updateChatLogRead(clientMsgId)
        Unit
    }

    override suspend fun selectAllMessages(): List<Message> = withContext(ioDispatcher) {
        queries.selectAllMessages().executeAsList().map(MessageDbMapper::fromRow)
    }

    override suspend fun selectSendingMessages(): List<SendingMessage> = withContext(ioDispatcher) {
        queries.selectSendingMessages().executeAsList().map { row ->
            SendingMessage(
                clientMsgID = row.clientMsgID,
                conversationID = row.conversationID,
                ex = row.ex,
            )
        }
    }

    override suspend fun deleteAllChatLogs() = withContext(ioDispatcher) {
        queries.deleteAllChatLogs()
        Unit
    }

    override suspend fun hideAllConversations() = withContext(ioDispatcher) {
        queries.hideAllConversations()
        Unit
    }

    private fun Message.toChatLogRow(): Local_chat_logs = Local_chat_logs(
        clientMsgID = clientMsgID,
        serverMsgID = serverMsgID,
        sendID = sendID,
        recvID = recvID,
        senderPlatformID = platformID?.toLong(),
        senderNickname = senderNickname,
        senderFaceUrl = senderFaceUrl,
        groupID = groupID,
        sessionType = sessionType?.value?.toLong(),
        msgFrom = msgFrom?.toLong(),
        contentType = contentType?.value?.toLong(),
        content = content,
        isRead = if (isRead == true) 1L else 0L,
        status = status?.value?.toLong(),
        seq = seq,
        sendTime = sendTime,
        createTime = createTime,
        attachedInfo = attachedInfo,
        ex = ex,
        localEx = localEx,
        isReact = 0L,
        isExternalExtensions = 0L,
        hasReadTime = null,
        conversationID = conversationID,
    )
}
