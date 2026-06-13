package com.kurban.xuehuaim.sdk.db

import com.kurban.xuehuaim.sdk.model.BlacklistInfo
import com.kurban.xuehuaim.sdk.model.ConversationInfo
import com.kurban.xuehuaim.sdk.model.FavoriteItem
import com.kurban.xuehuaim.sdk.model.FriendInfo
import com.kurban.xuehuaim.sdk.model.GroupInfo
import com.kurban.xuehuaim.sdk.model.GroupMemberInfo
import com.kurban.xuehuaim.sdk.model.Message
import com.kurban.xuehuaim.sdk.model.MomentInfo
import com.kurban.xuehuaim.sdk.model.SpaceInfo
import com.kurban.xuehuaim.sdk.model.UserInfo
import com.kurban.xuehuaim.sdk.platform.DatabaseDriverFactory
import com.kurban.xuehuaim.sdk.util.OpenImUtils
import com.kurban.xuehuaim.sdk.util.mergeConversation
import com.kurban.xuehuaim.sdk.util.normalizedForStorage
import com.kurban.xuehuaim.sdk.util.withParsedContent
import com.kurban.xuehuaim.sdk.util.withParsedLatestMsg
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DatabaseService(
    private val driverFactory: DatabaseDriverFactory,
    dbPath: String,
) {
    private val baseDbPath: String = dbPath.substringBeforeLast('/', missingDelimiterValue = dbPath)
    private val dbMutex = Mutex()
    private var database: ImDatabase? = null
    private val initDbPath: String = dbPath

    private suspend fun db(): ImDatabase {
        database?.let { return it }
        return dbMutex.withLock {
            database ?: createImDatabase(driverFactory, initDbPath).also { database = it }
        }
    }

    suspend fun switchSpace(userId: String) = dbMutex.withLock {
        database?.close()
        database = createImDatabase(driverFactory, "$baseDbPath/$userId.db")
    }

    suspend fun insertOrReplaceUser(user: UserInfo) = db().insertOrReplaceUser(user)
    suspend fun getAllUsers(): List<UserInfo> = db().getAllUsers()
    suspend fun insertOrReplaceConversation(conversation: ConversationInfo) {
        val incoming = conversation.withParsedLatestMsg().normalizedForStorage()
        val existing = db().getConversation(incoming.conversationID)
        val merged = if (existing != null) mergeConversation(existing, incoming) else incoming
        db().insertOrReplaceConversation(merged.normalizedForStorage())
    }

    suspend fun getAllConversations(): List<ConversationInfo> =
        db().getAllConversations().map { it.withParsedLatestMsg() }

    suspend fun getVisibleConversations(): List<ConversationInfo> =
        db().getVisibleConversations().map { it.withParsedLatestMsg() }

    suspend fun getConversationsPage(offset: Int, count: Int): List<ConversationInfo> =
        db().getConversationsPage(offset, count).map { it.withParsedLatestMsg() }

    suspend fun getConversation(conversationId: String): ConversationInfo? =
        db().getConversation(conversationId)?.withParsedLatestMsg()

    suspend fun resetConversation(conversationId: String) =
        db().resetConversation(conversationId)

    suspend fun deleteConversation(conversationId: String) =
        db().deleteConversation(conversationId)

    suspend fun updateConversationUnread(
        conversationId: String,
        unreadCount: Int,
        hasReadSeq: Long,
    ) = db().updateConversationUnread(conversationId, unreadCount, hasReadSeq)

    suspend fun getTotalUnreadCount(): Int = db().getTotalUnreadCount()
    suspend fun deleteChatLogsByConversation(conversationId: String) =
        db().deleteChatLogsByConversation(conversationId)

    suspend fun insertOrReplaceMessage(message: Message) =
        db().insertOrReplaceMessage(message.withParsedContent())

    suspend fun getMessages(conversationId: String, count: Long): List<Message> =
        db().getMessages(conversationId, count).map { it.withParsedContent() }

    suspend fun deleteMessage(clientMsgId: String) = db().deleteMessage(clientMsgId)
    suspend fun getVersionSync(tableName: String, entityId: String): VersionSyncInfo? =
        db().getVersionSync(tableName, entityId)

    suspend fun setVersionSync(
        tableName: String,
        entityId: String,
        versionID: String,
        version: Int,
    ) = db().setVersionSync(tableName, entityId, versionID, version)

    suspend fun deleteVersionSync(tableName: String, entityId: String) =
        db().deleteVersionSync(tableName, entityId)

    suspend fun insertOrReplaceGrabbedRedPacket(packetId: String, grabTime: Long) =
        db().insertOrReplaceGrabbedRedPacket(packetId, grabTime)

    suspend fun markRedPacketGrabbed(packetId: String) =
        insertOrReplaceGrabbedRedPacket(packetId, com.kurban.xuehuaim.sdk.util.System.currentTimeMillis())

    suspend fun selectGrabbedRedPacket(packetId: String): Long? =
        db().selectGrabbedRedPacket(packetId)

    suspend fun isRedPacketGrabbed(packetId: String): Boolean =
        selectGrabbedRedPacket(packetId) != null

    suspend fun selectGrabbedRedPacketIds(packetIds: List<String>): List<String> =
        db().selectGrabbedRedPacketIds(packetIds)

    suspend fun insertOrReplaceKv(key: String, value: String?, isGlobal: Boolean = false) =
        db().insertOrReplaceKv(key, value, isGlobal)

    suspend fun selectKv(key: String, isGlobal: Boolean = false): String? =
        db().selectKv(key, isGlobal)

    suspend fun deleteKv(key: String, isGlobal: Boolean = false) =
        db().deleteKv(key, isGlobal)

    suspend fun getMessageByClientMsgId(clientMsgId: String): Message? =
        db().selectMessageByClientMsgId(clientMsgId)?.withParsedContent()

    suspend fun updateChatLogContent(clientMsgId: String, content: String) =
        db().updateChatLogContent(clientMsgId, content)

    suspend fun selectAllMessages(): List<Message> =
        db().selectAllMessages().map { it.withParsedContent() }

    suspend fun selectSendingMessages(): List<SendingMessage> =
        db().selectSendingMessages()

    suspend fun deleteAllChatLogs() = db().deleteAllChatLogs()

    suspend fun hideAllConversations() = db().hideAllConversations()

    suspend fun getValue(key: String, isGlobal: Boolean = false): String? =
        selectKv(key, isGlobal)

    suspend fun setValue(key: String, value: String?, isGlobal: Boolean = false): Boolean {
        insertOrReplaceKv(key, value, isGlobal)
        return true
    }

    suspend fun removeValue(key: String, isGlobal: Boolean = false): Boolean {
        deleteKv(key, isGlobal)
        return true
    }

    suspend fun getSpaceInfo(): SpaceInfo =
        SpaceInfo(spaceName = OpenImUtils.generateSpaceName("current"))

    suspend fun updateMessageContentType(clientMsgId: String, contentType: Int) =
        db().updateMessageContentType(clientMsgId, contentType)

    suspend fun updateMessageLocalEx(clientMsgId: String, localEx: String) =
        db().updateMessageLocalEx(clientMsgId, localEx)

    suspend fun markMessageAsRead(clientMsgId: String) =
        db().markMessageAsRead(clientMsgId)

    suspend fun getMessagesByClientMsgIds(clientMsgIds: List<String>): List<Message> =
        clientMsgIds.mapNotNull { getMessageByClientMsgId(it) }

    suspend fun getMultipleConversations(conversationIds: List<String>): List<ConversationInfo> =
        conversationIds.mapNotNull { getConversation(it) }

    suspend fun searchConversations(name: String): List<ConversationInfo> =
        getVisibleConversations().filter {
            it.showName?.contains(name, ignoreCase = true) == true
        }

    suspend fun clearConversation(conversationId: String) {
        val conv = getConversation(conversationId) ?: return
        insertOrReplaceConversation(
            conv.copy(
                latestMsg = null,
                unreadCount = 0,
            ),
        )
    }

    suspend fun decrConversationUnreadCount(conversationId: String, count: Int) {
        val conv = getConversation(conversationId) ?: return
        updateConversationUnread(
            conversationId,
            (conv.unreadCount - count).coerceAtLeast(0),
            conv.hasReadSeq,
        )
    }

    suspend fun markConversationMessageAsReadDB(
        conversationId: String,
        clientMsgIds: List<String>,
    ): Int {
        var marked = 0
        clientMsgIds.forEach { clientMsgId ->
            val msg = getMessageByClientMsgId(clientMsgId) ?: return@forEach
            if (msg.isRead != true) {
                markMessageAsRead(clientMsgId)
                marked++
            }
        }
        return marked
    }

    suspend fun searchMessages(
        conversationId: String? = null,
        keyword: String? = null,
        messageTypes: List<Int>? = null,
        startTime: Long? = null,
        endTime: Long? = null,
        offset: Int = 0,
        count: Int = 40,
    ): List<Message> {
        val batchSize = (count + offset) * 3 + 200
        val source = if (!conversationId.isNullOrBlank()) {
            getMessages(conversationId, batchSize.toLong())
        } else {
            selectAllMessages()
        }
        return source.filter { msg ->
            val matchesKeyword = keyword.isNullOrBlank() || msg.content?.contains(keyword, true) == true
            val matchesType = messageTypes.isNullOrEmpty() ||
                messageTypes.contains(msg.contentType?.value)
            val time = msg.sendTime ?: msg.createTime ?: 0L
            val matchesStart = startTime == null || time >= startTime
            val matchesEnd = endTime == null || time <= endTime
            matchesKeyword && matchesType && matchesStart && matchesEnd
        }.drop(offset).take(count)
    }

    suspend fun getAllFriends(): List<FriendInfo> = db().getAllFriends()

    suspend fun getFriendsPage(offset: Int, count: Int, filterBlack: Boolean = false): List<FriendInfo> {
        val page = db().getFriendsPage(offset, count)
        if (!filterBlack) return page
        val blackIds = db().getBlackUserIds()
        return page.filter { it.userID !in blackIds }
    }

    suspend fun getFriendByUserId(userId: String): FriendInfo? = db().getFriendByUserId(userId)

    suspend fun insertOrReplaceFriend(friend: FriendInfo) = db().insertOrReplaceFriend(friend)

    suspend fun batchUpsertFriends(friends: List<FriendInfo>) = db().batchUpsertFriends(friends)

    suspend fun deleteFriend(userId: String) = db().deleteFriend(userId)

    suspend fun getBlackList(): List<BlacklistInfo> = db().getBlackList()

    suspend fun getBlackUserIds(): Set<String> = db().getBlackUserIds()

    suspend fun insertOrReplaceBlack(black: BlacklistInfo) = db().insertOrReplaceBlack(black)

    suspend fun deleteBlack(blockUserId: String) = db().deleteBlack(blockUserId)

    suspend fun getAllGroups(): List<GroupInfo> = db().getAllGroups()

    suspend fun insertOrReplaceGroup(group: GroupInfo) = db().insertOrReplaceGroup(group)

    suspend fun batchUpsertGroups(groups: List<GroupInfo>) = db().batchUpsertGroups(groups)

    suspend fun deleteGroup(groupId: String) = db().deleteGroup(groupId)

    suspend fun getGroupMembersPage(
        groupId: String,
        offset: Int,
        count: Int,
        filter: Int = 0,
    ): List<GroupMemberInfo> {
        val page = db().getGroupMembersPage(groupId, offset, count)
        if (filter <= 0) return page
        return page.filter { it.roleLevel?.value == filter }
    }

    suspend fun batchUpsertGroupMembers(members: List<GroupMemberInfo>) =
        db().batchUpsertGroupMembers(members)

    suspend fun deleteGroupMembers(groupId: String) = db().deleteGroupMembers(groupId)

    suspend fun getMomentsPage(offset: Int, count: Int): List<MomentInfo> =
        db().getMomentsPage(offset, count)

    suspend fun getMomentsByUserIdPage(userId: String, offset: Int, count: Int): List<MomentInfo> =
        db().getMomentsByUserIdPage(userId, offset, count)

    suspend fun batchUpsertMoments(moments: List<MomentInfo>) = db().batchUpsertMoments(moments)

    suspend fun deleteMoment(momentId: String) = db().deleteMoment(momentId)

    suspend fun getFavoritesPage(offset: Int, count: Int): List<FavoriteItem> =
        db().getFavoritesPage(offset, count)

    suspend fun batchUpsertFavorites(items: List<FavoriteItem>) = db().batchUpsertFavorites(items)

    suspend fun deleteFavorite(favoriteId: String) = db().deleteFavorite(favoriteId)

    suspend fun deleteFavoriteByTarget(targetType: String, targetId: String) =
        db().deleteFavoriteByTarget(targetType, targetId)

    suspend fun insertOrReplaceSendingMessage(record: SendingMessage) =
        db().insertOrReplaceSendingMessage(record)

    suspend fun deleteSendingMessage(clientMsgId: String) = db().deleteSendingMessage(clientMsgId)

    suspend fun upsertUploadTask(record: UploadRecord) = db().insertOrReplaceUpload(record)

    suspend fun getUploadByHashAndName(hash: String, name: String): UploadRecord? =
        db().getUploadByHashAndName(hash, name)

    suspend fun deleteUpload(uploadId: String) = db().deleteUpload(uploadId)

    suspend fun markConversationNotInGroup(groupId: String) {
        val conversationId = OpenImUtils.genGroupConversationID(groupId)
        getConversation(conversationId)?.let { conv ->
            insertOrReplaceConversation(conv.copy(isNotInGroup = true))
        }
    }
}
