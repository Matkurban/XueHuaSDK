package com.kurban.xuehuaim.sdk.db

import com.kurban.xuehuaim.sdk.model.ConversationInfo
import com.kurban.xuehuaim.sdk.model.Message
import com.kurban.xuehuaim.sdk.model.UserInfo
import com.kurban.xuehuaim.sdk.platform.ioDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

internal class SqlDelightImDatabase(
    database: OpenIMDatabase,
) : ImDatabase {
    private val queries = database.openIMDatabaseQueries
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun switchSpace(userId: String) = withContext(ioDispatcher) { }

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
        queries.insertOrReplaceChatLog(
            Local_chat_logs(
                clientMsgID = message.clientMsgID,
                serverMsgID = message.serverMsgID,
                sendID = message.sendID,
                recvID = message.recvID,
                senderPlatformID = message.platformID?.toLong(),
                senderNickname = message.senderNickname,
                senderFaceUrl = message.senderFaceUrl,
                groupID = message.groupID,
                sessionType = message.sessionType?.value?.toLong(),
                msgFrom = message.msgFrom?.toLong(),
                contentType = message.contentType?.value?.toLong(),
                content = message.content,
                isRead = if (message.isRead == true) 1L else 0L,
                status = message.status?.value?.toLong(),
                seq = message.seq,
                sendTime = message.sendTime,
                createTime = message.createTime,
                attachedInfo = message.attachedInfo,
                ex = message.ex,
                localEx = message.localEx,
                isReact = 0L,
                isExternalExtensions = 0L,
                hasReadTime = null,
                conversationID = message.conversationID,
            ),
        )
        Unit
    }

    override suspend fun getMessages(conversationId: String, count: Long): List<Message> =
        withContext(ioDispatcher) {
            queries.selectMessagesByConversation(conversationId, count).executeAsList().map { row ->
                Message(
                    clientMsgID = row.clientMsgID,
                    serverMsgID = row.serverMsgID,
                    sendID = row.sendID,
                    recvID = row.recvID,
                    platformID = row.senderPlatformID?.toInt(),
                    senderNickname = row.senderNickname,
                    senderFaceUrl = row.senderFaceUrl,
                    groupID = row.groupID,
                    sessionType = row.sessionType?.toInt()?.let { v ->
                        com.kurban.xuehuaim.sdk.enum.ConversationType.entries.find { it.value == v }
                    },
                    msgFrom = row.msgFrom?.toInt(),
                    contentType = row.contentType?.toInt()?.let { v ->
                        com.kurban.xuehuaim.sdk.enum.MessageType.entries.find { it.value == v }
                    },
                    content = row.content,
                    isRead = row.isRead == 1L,
                    status = row.status?.toInt()?.let { v ->
                        com.kurban.xuehuaim.sdk.enum.MessageStatus.entries.find { it.value == v }
                    },
                    seq = row.seq,
                    sendTime = row.sendTime,
                    createTime = row.createTime,
                    attachedInfo = row.attachedInfo,
                    ex = row.ex,
                    localEx = row.localEx,
                    conversationID = row.conversationID,
                )
            }
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
}
