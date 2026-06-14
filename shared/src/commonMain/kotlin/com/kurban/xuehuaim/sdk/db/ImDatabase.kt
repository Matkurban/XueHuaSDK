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
    suspend fun getConversationMaxNormalMsgSeq(conversationId: String): Long
    suspend fun getAllConversationMaxNormalMsgSeqs(): Map<String, Long>
    suspend fun getMessagesBySeqDesc(
        conversationId: String,
        count: Int,
        beforeSeq: Long? = null,
    ): List<Message>
    suspend fun deleteMessage(clientMsgId: String)
    suspend fun getVersionSync(tableName: String, entityId: String): VersionSyncInfo?
    suspend fun setVersionSync(
        tableName: String,
        entityId: String,
        versionID: String,
        version: Int,
        uidList: List<String> = emptyList(),
    )
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

    suspend fun getAllFriends(): List<FriendInfo>
    suspend fun getFriendsPage(offset: Int, count: Int): List<FriendInfo>
    suspend fun getFriendByUserId(userId: String): FriendInfo?
    suspend fun insertOrReplaceFriend(friend: FriendInfo)
    suspend fun batchUpsertFriends(friends: List<FriendInfo>)
    suspend fun deleteFriend(userId: String)
    suspend fun deleteAllFriends()

    suspend fun getBlackList(): List<BlacklistInfo>
    suspend fun getBlackUserIds(): Set<String>
    suspend fun insertOrReplaceBlack(black: BlacklistInfo)
    suspend fun deleteBlack(blockUserId: String)
    suspend fun deleteAllBlacks()

    suspend fun getAllGroups(): List<GroupInfo>
    suspend fun insertOrReplaceGroup(group: GroupInfo)
    suspend fun batchUpsertGroups(groups: List<GroupInfo>)
    suspend fun deleteGroup(groupId: String)
    suspend fun deleteAllGroups()

    suspend fun getGroupMembersPage(groupId: String, offset: Int, count: Int): List<GroupMemberInfo>
    suspend fun insertOrReplaceGroupMember(member: GroupMemberInfo)
    suspend fun batchUpsertGroupMembers(members: List<GroupMemberInfo>)
    suspend fun deleteGroupMembers(groupId: String)

    suspend fun getMomentsPage(offset: Int, count: Int): List<MomentInfo>
    suspend fun getMomentsByUserIdPage(userId: String, offset: Int, count: Int): List<MomentInfo>
    suspend fun getMomentById(momentId: String): MomentInfo?
    suspend fun insertOrReplaceMoment(moment: MomentInfo)
    suspend fun batchUpsertMoments(moments: List<MomentInfo>)
    suspend fun deleteMoment(momentId: String)
    suspend fun deleteAllMoments()

    suspend fun getFavoritesPage(offset: Int, count: Int): List<FavoriteItem>
    suspend fun insertOrReplaceFavorite(item: FavoriteItem)
    suspend fun batchUpsertFavorites(items: List<FavoriteItem>)
    suspend fun deleteFavorite(favoriteId: String)
    suspend fun deleteFavoriteByTarget(targetType: String, targetId: String)

    suspend fun insertOrReplaceSendingMessage(record: SendingMessage)
    suspend fun deleteSendingMessage(clientMsgId: String)

    suspend fun insertOrReplaceUpload(record: UploadRecord)
    suspend fun getUpload(uploadId: String): UploadRecord?
    suspend fun getUploadByHashAndName(hash: String, name: String): UploadRecord?
    suspend fun deleteUpload(uploadId: String)

    suspend fun getNotificationSeq(conversationId: String): Long
    suspend fun setNotificationSeq(conversationId: String, seq: Long)
    suspend fun getAllNotificationSeqs(): Map<String, Long>
}

internal expect suspend fun createImDatabase(
    driverFactory: DatabaseDriverFactory,
    dbPath: String,
): ImDatabase
