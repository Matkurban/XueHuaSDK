package com.kurban.xuehuaim.sdk.db

import app.cash.sqldelight.db.SqlDriver
import com.kurban.xuehuaim.sdk.model.BlacklistInfo
import com.kurban.xuehuaim.sdk.model.ConversationInfo
import com.kurban.xuehuaim.sdk.model.FavoriteItem
import com.kurban.xuehuaim.sdk.model.FriendInfo
import com.kurban.xuehuaim.sdk.model.GroupInfo
import com.kurban.xuehuaim.sdk.model.GroupMemberInfo
import com.kurban.xuehuaim.sdk.model.Message
import com.kurban.xuehuaim.sdk.model.MomentInfo
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
                    uidList = VersionSyncUidListCodec.decode(row.uidList),
                )
            }
        }

    override suspend fun setVersionSync(
        tableName: String,
        entityId: String,
        versionID: String,
        version: Int,
        uidList: List<String>,
    ) = withContext(ioDispatcher) {
        queries.insertOrReplaceVersionSync(
            Local_version_sync(
                id = "$tableName|$entityId",
                tableName = tableName,
                entityID = entityId,
                versionID = versionID,
                version = version.toLong(),
                uidList = VersionSyncUidListCodec.encode(uidList),
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

    override suspend fun getAllFriends(): List<FriendInfo> = withContext(ioDispatcher) {
        queries.selectAllFriends().executeAsList().map(SocialDbMappers::friendFromRow)
    }

    override suspend fun getFriendsPage(offset: Int, count: Int): List<FriendInfo> =
        withContext(ioDispatcher) {
            queries.selectFriendsPage(count.toLong(), offset.toLong())
                .executeAsList()
                .map(SocialDbMappers::friendFromRow)
        }

    override suspend fun getFriendByUserId(userId: String): FriendInfo? =
        withContext(ioDispatcher) {
            queries.selectFriendByUserID(userId).executeAsOneOrNull()
                ?.let(SocialDbMappers::friendFromRow)
        }

    override suspend fun insertOrReplaceFriend(friend: FriendInfo) = withContext(ioDispatcher) {
        queries.insertOrReplaceFriend(SocialDbMappers.friendToRow(friend))
        Unit
    }

    override suspend fun batchUpsertFriends(friends: List<FriendInfo>) = withContext(ioDispatcher) {
        friends.forEach { queries.insertOrReplaceFriend(SocialDbMappers.friendToRow(it)) }
        Unit
    }

    override suspend fun deleteFriend(userId: String) = withContext(ioDispatcher) {
        queries.deleteFriend(userId)
        Unit
    }

    override suspend fun deleteAllFriends() = withContext(ioDispatcher) {
        queries.deleteAllFriends()
        Unit
    }

    override suspend fun getBlackList(): List<BlacklistInfo> = withContext(ioDispatcher) {
        queries.selectAllBlacks().executeAsList().map(SocialDbMappers::blackFromRow)
    }

    override suspend fun getBlackUserIds(): Set<String> = withContext(ioDispatcher) {
        queries.selectBlackUserIDs().executeAsList().toSet()
    }

    override suspend fun insertOrReplaceBlack(black: BlacklistInfo) = withContext(ioDispatcher) {
        queries.insertOrReplaceBlack(SocialDbMappers.blackToRow(black))
        Unit
    }

    override suspend fun deleteBlack(blockUserId: String) = withContext(ioDispatcher) {
        queries.deleteBlack(blockUserId)
        Unit
    }

    override suspend fun deleteAllBlacks() = withContext(ioDispatcher) {
        queries.deleteAllBlacks()
        Unit
    }

    override suspend fun getAllGroups(): List<GroupInfo> = withContext(ioDispatcher) {
        queries.selectAllGroups().executeAsList().map(SocialDbMappers::groupFromRow)
    }

    override suspend fun insertOrReplaceGroup(group: GroupInfo) = withContext(ioDispatcher) {
        queries.insertOrReplaceGroup(SocialDbMappers.groupToRow(group))
        Unit
    }

    override suspend fun batchUpsertGroups(groups: List<GroupInfo>) = withContext(ioDispatcher) {
        groups.forEach { queries.insertOrReplaceGroup(SocialDbMappers.groupToRow(it)) }
        Unit
    }

    override suspend fun deleteGroup(groupId: String) = withContext(ioDispatcher) {
        queries.deleteGroup(groupId)
        Unit
    }

    override suspend fun deleteAllGroups() = withContext(ioDispatcher) {
        queries.deleteAllGroups()
        Unit
    }

    override suspend fun getGroupMembersPage(
        groupId: String,
        offset: Int,
        count: Int,
    ): List<GroupMemberInfo> = withContext(ioDispatcher) {
        queries.selectGroupMembersPage(groupId, count.toLong(), offset.toLong())
            .executeAsList()
            .map(SocialDbMappers::groupMemberFromRow)
    }

    override suspend fun insertOrReplaceGroupMember(member: GroupMemberInfo) =
        withContext(ioDispatcher) {
            queries.insertOrReplaceGroupMember(SocialDbMappers.groupMemberToRow(member))
            Unit
        }

    override suspend fun batchUpsertGroupMembers(members: List<GroupMemberInfo>) =
        withContext(ioDispatcher) {
            members.forEach { queries.insertOrReplaceGroupMember(SocialDbMappers.groupMemberToRow(it)) }
            Unit
        }

    override suspend fun deleteGroupMembers(groupId: String) = withContext(ioDispatcher) {
        queries.deleteGroupMembersByGroupID(groupId)
        Unit
    }

    override suspend fun getMomentsPage(offset: Int, count: Int): List<MomentInfo> =
        withContext(ioDispatcher) {
            queries.selectMomentsPage(count.toLong(), offset.toLong())
                .executeAsList()
                .map(SocialDbMappers::momentFromRow)
        }

    override suspend fun getMomentsByUserIdPage(
        userId: String,
        offset: Int,
        count: Int,
    ): List<MomentInfo> = withContext(ioDispatcher) {
        queries.selectMomentsByUserIDPage(userId, count.toLong(), offset.toLong())
            .executeAsList()
            .map(SocialDbMappers::momentFromRow)
    }

    override suspend fun insertOrReplaceMoment(moment: MomentInfo) = withContext(ioDispatcher) {
        queries.insertOrReplaceMoment(SocialDbMappers.momentToRow(moment))
        Unit
    }

    override suspend fun batchUpsertMoments(moments: List<MomentInfo>) = withContext(ioDispatcher) {
        moments.forEach { queries.insertOrReplaceMoment(SocialDbMappers.momentToRow(it)) }
        Unit
    }

    override suspend fun deleteMoment(momentId: String) = withContext(ioDispatcher) {
        queries.deleteMomentByID(momentId)
        Unit
    }

    override suspend fun deleteAllMoments() = withContext(ioDispatcher) {
        queries.deleteAllMoments()
        Unit
    }

    override suspend fun getFavoritesPage(offset: Int, count: Int): List<FavoriteItem> =
        withContext(ioDispatcher) {
            queries.selectFavoritesPage(count.toLong(), offset.toLong())
                .executeAsList()
                .map(SocialDbMappers::favoriteFromRow)
        }

    override suspend fun insertOrReplaceFavorite(item: FavoriteItem) = withContext(ioDispatcher) {
        queries.insertOrReplaceFavorite(SocialDbMappers.favoriteToRow(item))
        Unit
    }

    override suspend fun batchUpsertFavorites(items: List<FavoriteItem>) =
        withContext(ioDispatcher) {
            items.forEach { queries.insertOrReplaceFavorite(SocialDbMappers.favoriteToRow(it)) }
            Unit
        }

    override suspend fun deleteFavorite(favoriteId: String) = withContext(ioDispatcher) {
        queries.deleteFavoriteByID(favoriteId)
        Unit
    }

    override suspend fun deleteFavoriteByTarget(targetType: String, targetId: String) =
        withContext(ioDispatcher) {
            queries.deleteFavoriteByTarget(targetType, targetId)
            Unit
        }

    override suspend fun insertOrReplaceSendingMessage(record: SendingMessage) =
        withContext(ioDispatcher) {
            queries.insertOrReplaceSendingMessage(
                Local_sending_messages(
                    clientMsgID = record.clientMsgID,
                    conversationID = record.conversationID,
                    ex = record.ex,
                ),
            )
            Unit
        }

    override suspend fun deleteSendingMessage(clientMsgId: String) = withContext(ioDispatcher) {
        queries.deleteSendingMessage(clientMsgId)
        Unit
    }

    override suspend fun insertOrReplaceUpload(record: UploadRecord) = withContext(ioDispatcher) {
        queries.insertOrReplaceUpload(
            Local_uploads(
                uploadID = record.uploadID,
                hash = record.hash,
                name = record.name,
                fileSize = record.fileSize,
                partSize = record.partSize,
                partNum = record.partNum?.toLong(),
                uploadedParts = record.uploadedParts,
                updateTime = record.updateTime,
            ),
        )
        Unit
    }

    override suspend fun getUpload(uploadId: String): UploadRecord? = withContext(ioDispatcher) {
        queries.selectUploadByID(uploadId).executeAsOneOrNull()?.toUploadRecord()
    }

    override suspend fun getUploadByHashAndName(hash: String, name: String): UploadRecord? =
        withContext(ioDispatcher) {
            queries.selectUploadByHashAndName(hash, name).executeAsOneOrNull()?.toUploadRecord()
        }

    override suspend fun deleteUpload(uploadId: String) = withContext(ioDispatcher) {
        queries.deleteUploadByID(uploadId)
        Unit
    }

    override suspend fun getNotificationSeq(conversationId: String): Long =
        withContext(ioDispatcher) {
            queries.selectNotificationSeq(conversationId).executeAsOneOrNull() ?: 0L
        }

    override suspend fun setNotificationSeq(conversationId: String, seq: Long) =
        withContext(ioDispatcher) {
            queries.insertOrReplaceNotificationSeq(
                Local_notification_seqs(
                    conversationID = conversationId,
                    seq = seq,
                    updateTime = com.kurban.xuehuaim.sdk.util.System.currentTimeMillis(),
                ),
            )
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

    private fun Local_uploads.toUploadRecord(): UploadRecord = UploadRecord(
        uploadID = uploadID,
        hash = hash,
        name = name,
        fileSize = fileSize,
        partSize = partSize,
        partNum = partNum?.toInt(),
        uploadedParts = uploadedParts,
        updateTime = updateTime,
    )
}
