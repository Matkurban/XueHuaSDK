package com.kurban.xuehuaim.sdk.db

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
    private val friends = mutableListOf<FriendInfo>()
    private val blacks = mutableListOf<BlacklistInfo>()
    private val groups = mutableListOf<GroupInfo>()
    private val groupMembers = mutableListOf<GroupMemberInfo>()
    private val moments = mutableListOf<MomentInfo>()
    private val favorites = mutableListOf<FavoriteItem>()
    private val uploads = mutableMapOf<String, UploadRecord>()
    private val notificationSeqs = mutableMapOf<String, Long>()

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

    override suspend fun getAllFriends(): List<FriendInfo> = withContext(ioDispatcher) {
        mutex.withLock { friends.sortedByDescending { it.createTime ?: 0 }.toList() }
    }

    override suspend fun getFriendsPage(offset: Int, count: Int): List<FriendInfo> =
        withContext(ioDispatcher) {
            mutex.withLock {
                friends.sortedByDescending { it.createTime ?: 0 }.drop(offset).take(count)
            }
        }

    override suspend fun getFriendByUserId(userId: String): FriendInfo? = withContext(ioDispatcher) {
        mutex.withLock { friends.find { it.userID == userId } }
    }

    override suspend fun insertOrReplaceFriend(friend: FriendInfo) = withContext(ioDispatcher) {
        mutex.withLock {
            friends.removeAll { it.userID == friend.userID }
            friends.add(friend)
            Unit
        }
    }

    override suspend fun batchUpsertFriends(friendsList: List<FriendInfo>) =
        withContext(ioDispatcher) {
            mutex.withLock {
                friendsList.forEach { friend ->
                    friends.removeAll { it.userID == friend.userID }
                    friends.add(friend)
                }
                Unit
            }
        }

    override suspend fun deleteFriend(userId: String) = withContext(ioDispatcher) {
        mutex.withLock {
            friends.removeAll { it.userID == userId }
            Unit
        }
    }

    override suspend fun deleteAllFriends() = withContext(ioDispatcher) {
        mutex.withLock {
            friends.clear()
            Unit
        }
    }

    override suspend fun getBlackList(): List<BlacklistInfo> = withContext(ioDispatcher) {
        mutex.withLock { blacks.toList() }
    }

    override suspend fun getBlackUserIds(): Set<String> = withContext(ioDispatcher) {
        mutex.withLock { blacks.map { it.blockUserID }.toSet() }
    }

    override suspend fun insertOrReplaceBlack(black: BlacklistInfo) = withContext(ioDispatcher) {
        mutex.withLock {
            blacks.removeAll { it.blockUserID == black.blockUserID }
            blacks.add(black)
            Unit
        }
    }

    override suspend fun deleteBlack(blockUserId: String) = withContext(ioDispatcher) {
        mutex.withLock {
            blacks.removeAll { it.blockUserID == blockUserId }
            Unit
        }
    }

    override suspend fun deleteAllBlacks() = withContext(ioDispatcher) {
        mutex.withLock {
            blacks.clear()
            Unit
        }
    }

    override suspend fun getAllGroups(): List<GroupInfo> = withContext(ioDispatcher) {
        mutex.withLock { groups.sortedByDescending { it.createTime ?: 0 }.toList() }
    }

    override suspend fun insertOrReplaceGroup(group: GroupInfo) = withContext(ioDispatcher) {
        mutex.withLock {
            groups.removeAll { it.groupID == group.groupID }
            groups.add(group)
            Unit
        }
    }

    override suspend fun batchUpsertGroups(groupsList: List<GroupInfo>) = withContext(ioDispatcher) {
        mutex.withLock {
            groupsList.forEach { group ->
                groups.removeAll { it.groupID == group.groupID }
                groups.add(group)
            }
            Unit
        }
    }

    override suspend fun deleteGroup(groupId: String) = withContext(ioDispatcher) {
        mutex.withLock {
            groups.removeAll { it.groupID == groupId }
            Unit
        }
    }

    override suspend fun getGroupMembersPage(
        groupId: String,
        offset: Int,
        count: Int,
    ): List<GroupMemberInfo> = withContext(ioDispatcher) {
        mutex.withLock {
            groupMembers.filter { it.groupID == groupId }
                .sortedBy { it.joinTime ?: 0 }
                .drop(offset)
                .take(count)
        }
    }

    override suspend fun insertOrReplaceGroupMember(member: GroupMemberInfo) =
        withContext(ioDispatcher) {
            mutex.withLock {
                groupMembers.removeAll { it.groupID == member.groupID && it.userID == member.userID }
                groupMembers.add(member)
                Unit
            }
        }

    override suspend fun batchUpsertGroupMembers(members: List<GroupMemberInfo>) =
        withContext(ioDispatcher) {
            mutex.withLock {
                members.forEach { member ->
                    groupMembers.removeAll { it.groupID == member.groupID && it.userID == member.userID }
                    groupMembers.add(member)
                }
                Unit
            }
        }

    override suspend fun deleteGroupMembers(groupId: String) = withContext(ioDispatcher) {
        mutex.withLock {
            groupMembers.removeAll { it.groupID == groupId }
            Unit
        }
    }

    override suspend fun getMomentsPage(offset: Int, count: Int): List<MomentInfo> =
        withContext(ioDispatcher) {
            mutex.withLock {
                moments.sortedByDescending { it.createTime.orEmpty() }.drop(offset).take(count)
            }
        }

    override suspend fun getMomentsByUserIdPage(
        userId: String,
        offset: Int,
        count: Int,
    ): List<MomentInfo> = withContext(ioDispatcher) {
        mutex.withLock {
            moments.filter { it.userID == userId }
                .sortedByDescending { it.createTime.orEmpty() }
                .drop(offset)
                .take(count)
        }
    }

    override suspend fun insertOrReplaceMoment(moment: MomentInfo) = withContext(ioDispatcher) {
        mutex.withLock {
            moments.removeAll { it.momentID == moment.momentID }
            moments.add(moment)
            Unit
        }
    }

    override suspend fun batchUpsertMoments(momentsList: List<MomentInfo>) =
        withContext(ioDispatcher) {
            mutex.withLock {
                momentsList.forEach { moment ->
                    moments.removeAll { it.momentID == moment.momentID }
                    moments.add(moment)
                }
                Unit
            }
        }

    override suspend fun deleteMoment(momentId: String) = withContext(ioDispatcher) {
        mutex.withLock {
            moments.removeAll { it.momentID == momentId }
            Unit
        }
    }

    override suspend fun deleteAllMoments() = withContext(ioDispatcher) {
        mutex.withLock {
            moments.clear()
            Unit
        }
    }

    override suspend fun getFavoritesPage(offset: Int, count: Int): List<FavoriteItem> =
        withContext(ioDispatcher) {
            mutex.withLock {
                favorites.sortedByDescending { it.createTime ?: 0 }.drop(offset).take(count)
            }
        }

    override suspend fun insertOrReplaceFavorite(item: FavoriteItem) = withContext(ioDispatcher) {
        mutex.withLock {
            favorites.removeAll { it.favoriteID == item.favoriteID }
            favorites.add(item)
            Unit
        }
    }

    override suspend fun batchUpsertFavorites(items: List<FavoriteItem>) =
        withContext(ioDispatcher) {
            mutex.withLock {
                items.forEach { item ->
                    favorites.removeAll { it.favoriteID == item.favoriteID }
                    favorites.add(item)
                }
                Unit
            }
        }

    override suspend fun deleteFavorite(favoriteId: String) = withContext(ioDispatcher) {
        mutex.withLock {
            favorites.removeAll { it.favoriteID == favoriteId }
            Unit
        }
    }

    override suspend fun deleteFavoriteByTarget(targetType: String, targetId: String) =
        withContext(ioDispatcher) {
            mutex.withLock {
                favorites.removeAll { it.targetType == targetType && it.targetID == targetId }
                Unit
            }
        }

    override suspend fun insertOrReplaceSendingMessage(record: SendingMessage) =
        withContext(ioDispatcher) {
            mutex.withLock {
                sendingMessages.removeAll { it.clientMsgID == record.clientMsgID }
                sendingMessages.add(record)
                Unit
            }
        }

    override suspend fun deleteSendingMessage(clientMsgId: String) = withContext(ioDispatcher) {
        mutex.withLock {
            sendingMessages.removeAll { it.clientMsgID == clientMsgId }
            Unit
        }
    }

    override suspend fun insertOrReplaceUpload(record: UploadRecord) = withContext(ioDispatcher) {
        mutex.withLock {
            uploads[record.uploadID] = record
            Unit
        }
    }

    override suspend fun getUpload(uploadId: String): UploadRecord? = withContext(ioDispatcher) {
        mutex.withLock { uploads[uploadId] }
    }

    override suspend fun deleteUpload(uploadId: String) = withContext(ioDispatcher) {
        mutex.withLock {
            uploads.remove(uploadId)
            Unit
        }
    }

    override suspend fun getNotificationSeq(conversationId: String): Long =
        withContext(ioDispatcher) {
            mutex.withLock { notificationSeqs[conversationId] ?: 0L }
        }

    override suspend fun setNotificationSeq(conversationId: String, seq: Long) =
        withContext(ioDispatcher) {
            mutex.withLock {
                notificationSeqs[conversationId] = seq
                Unit
            }
        }
}
