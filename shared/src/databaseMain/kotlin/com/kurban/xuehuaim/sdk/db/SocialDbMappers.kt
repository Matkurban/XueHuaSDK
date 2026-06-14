package com.kurban.xuehuaim.sdk.db

import com.kurban.xuehuaim.sdk.enum.GroupRoleLevel
import com.kurban.xuehuaim.sdk.enum.GroupType
import com.kurban.xuehuaim.sdk.model.BlacklistInfo
import com.kurban.xuehuaim.sdk.model.FavoriteItem
import com.kurban.xuehuaim.sdk.model.FriendInfo
import com.kurban.xuehuaim.sdk.model.GroupInfo
import com.kurban.xuehuaim.sdk.model.GroupMemberInfo
import com.kurban.xuehuaim.sdk.model.MomentCommentWithUser
import com.kurban.xuehuaim.sdk.model.MomentInfo
import com.kurban.xuehuaim.sdk.model.MomentLikeWithUser
import kotlinx.serialization.json.Json

internal object SocialDbMappers {
    private val json = Json { ignoreUnknownKeys = true }

    fun friendFromRow(row: Local_friends): FriendInfo = FriendInfo(
        ownerUserID = row.ownerUserID,
        userID = row.friendUserID,
        nickname = row.nickname,
        faceURL = row.faceURL,
        remark = row.remark,
        ex = row.ex,
        createTime = row.createTime,
        addSource = row.addSource?.toInt(),
        operatorUserID = row.operatorUserID,
        isPinned = row.isPinned?.let { it == 1L },
    )

    fun friendToRow(friend: FriendInfo): Local_friends = Local_friends(
        friendUserID = friend.userID,
        ownerUserID = friend.ownerUserID,
        userID = friend.userID,
        nickname = friend.nickname,
        faceURL = friend.faceURL,
        remark = friend.remark,
        ex = friend.ex,
        createTime = friend.createTime,
        addSource = friend.addSource?.toLong(),
        operatorUserID = friend.operatorUserID,
        isPinned = if (friend.isPinned == true) 1L else 0L,
    )

    fun blackFromRow(row: Local_blacks): BlacklistInfo = BlacklistInfo(
        ownerUserID = row.ownerUserID,
        blockUserID = row.blockUserID,
        userID = row.userID,
        nickname = row.nickname,
        faceURL = row.faceURL,
        createTime = row.createTime,
        ex = row.ex,
    )

    fun blackToRow(black: BlacklistInfo): Local_blacks = Local_blacks(
        id = "${black.ownerUserID}_${black.blockUserID}",
        ownerUserID = black.ownerUserID,
        blockUserID = black.blockUserID,
        userID = black.userID ?: black.blockUserID,
        nickname = black.nickname,
        faceURL = black.faceURL,
        gender = null,
        createTime = black.createTime,
        addSource = null,
        operatorUserID = null,
        ex = black.ex,
    )

    fun groupFromRow(row: Local_groups): GroupInfo = GroupInfo(
        groupID = row.groupID,
        groupName = row.groupName,
        notification = row.notification,
        introduction = row.introduction,
        faceURL = row.faceURL,
        ownerUserID = row.ownerUserID,
        createTime = row.createTime,
        memberCount = row.memberCount?.toInt(),
        status = row.status?.toInt(),
        creatorUserID = row.creatorUserID,
        groupType = row.groupType?.toInt()?.let { GroupType.entries.find { t -> t.value == it } },
        ex = row.ex,
    )

    fun groupToRow(group: GroupInfo): Local_groups = Local_groups(
        groupID = group.groupID,
        groupName = group.groupName,
        notification = group.notification,
        introduction = group.introduction,
        faceURL = group.faceURL,
        ownerUserID = group.ownerUserID,
        createTime = group.createTime,
        memberCount = group.memberCount?.toLong(),
        status = group.status?.toLong(),
        creatorUserID = group.creatorUserID,
        groupType = group.groupType?.value?.toLong(),
        ex = group.ex,
        needVerification = null,
        lookMemberInfo = null,
        applyMemberFriend = null,
        notificationUpdateTime = null,
        notificationUserID = null,
    )

    fun groupMemberFromRow(row: Local_group_members): GroupMemberInfo = GroupMemberInfo(
        groupID = row.groupID,
        userID = row.userID,
        nickname = row.nickname,
        faceURL = row.faceURL,
        roleLevel = row.roleLevel?.toInt()
            ?.let { GroupRoleLevel.entries.find { r -> r.value == it } },
        joinTime = row.joinTime,
        joinSource = row.joinSource?.toInt(),
        operatorUserID = row.operatorUserID,
        ex = row.ex,
        muteEndTime = row.muteEndTime,
        inviterUserID = row.inviterUserID,
    )

    fun groupMemberToRow(member: GroupMemberInfo): Local_group_members = Local_group_members(
        id = "${member.groupID}_${member.userID}",
        groupID = member.groupID,
        userID = member.userID,
        nickname = member.nickname,
        faceURL = member.faceURL,
        roleLevel = member.roleLevel?.value?.toLong(),
        joinTime = member.joinTime,
        joinSource = member.joinSource?.toLong(),
        operatorUserID = member.operatorUserID,
        ex = member.ex,
        muteEndTime = member.muteEndTime,
        appMangerLevel = null,
        inviterUserID = member.inviterUserID,
    )

    fun momentFromRow(row: Local_moments): MomentInfo = MomentInfo(
        momentID = row.momentID,
        userID = row.userID,
        content = row.content,
        media = row.media?.let { decodeJsonList(it) } ?: emptyList(),
        visibleType = row.visibleType?.toInt(),
        visibleGroupIDs = row.visibleGroupIDs?.let { decodeStringList(it) } ?: emptyList(),
        status = row.status?.toInt(),
        createTime = row.createTime,
        updateTime = row.updateTime,
        likeCount = row.likeCount?.toInt(),
        commentCount = row.commentCount?.toInt(),
        extra = row.extra,
        likes = row.likes?.let { decodeLikes(it) } ?: emptyList(),
        comments = row.comments?.let { decodeComments(it) } ?: emptyList(),
    )

    fun momentToRow(moment: MomentInfo): Local_moments = Local_moments(
        momentID = moment.momentID,
        userID = moment.userID,
        content = moment.content,
        media = json.encodeToString(moment.media),
        visibleType = moment.visibleType?.toLong(),
        visibleGroupIDs = encodeStringList(moment.visibleGroupIDs),
        status = moment.status?.toLong(),
        createTime = moment.createTime,
        updateTime = moment.updateTime,
        likeCount = moment.likeCount?.toLong(),
        commentCount = moment.commentCount?.toLong(),
        extra = moment.extra,
        likes = json.encodeToString(moment.likes),
        comments = json.encodeToString(moment.comments),
    )

    fun favoriteFromRow(row: Local_favorites): FavoriteItem = FavoriteItem(
        favoriteID = row.favoriteID,
        userID = row.userID,
        targetType = row.targetType,
        targetID = row.targetID,
        data = row.data_,
        createTime = row.createTime,
    )

    fun favoriteToRow(item: FavoriteItem): Local_favorites = Local_favorites(
        favoriteID = item.favoriteID,
        userID = item.userID,
        targetType = item.targetType,
        targetID = item.targetID,
        data_ = item.data,
        createTime = item.createTime,
    )

    private inline fun <reified T> decodeJsonList(raw: String): List<T> =
        runCatching { json.decodeFromString<List<T>>(raw) }.getOrDefault(emptyList())

    private fun decodeLikes(raw: String): List<MomentLikeWithUser> = decodeJsonList(raw)
    private fun decodeComments(raw: String): List<MomentCommentWithUser> = decodeJsonList(raw)

    private fun decodeStringList(raw: String): List<String> =
        runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())

    private fun encodeStringList(values: List<String>): String? =
        values.takeIf { it.isNotEmpty() }?.let { json.encodeToString(it) }
}
