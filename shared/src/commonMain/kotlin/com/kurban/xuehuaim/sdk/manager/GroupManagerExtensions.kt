package com.kurban.xuehuaim.sdk.manager

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.enum.ConversationType
import com.kurban.xuehuaim.sdk.enum.FavoriteType
import com.kurban.xuehuaim.sdk.enum.GroupRoleLevel
import com.kurban.xuehuaim.sdk.enum.MessageStatus
import com.kurban.xuehuaim.sdk.enum.MessageType
import com.kurban.xuehuaim.sdk.event.FavoriteEvent
import com.kurban.xuehuaim.sdk.event.MomentsEvent
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.model.AdvancedTextElem
import com.kurban.xuehuaim.sdk.model.AppealCaptcha
import com.kurban.xuehuaim.sdk.model.AppealUploadResult
import com.kurban.xuehuaim.sdk.model.ApplicationVersionInfo
import com.kurban.xuehuaim.sdk.model.CallSignalElem
import com.kurban.xuehuaim.sdk.model.CreateReportResult
import com.kurban.xuehuaim.sdk.model.FavoriteItem
import com.kurban.xuehuaim.sdk.model.FavoriteListResponse
import com.kurban.xuehuaim.sdk.model.FileElem
import com.kurban.xuehuaim.sdk.model.FriendInfo
import com.kurban.xuehuaim.sdk.model.GroupInfo
import com.kurban.xuehuaim.sdk.model.GroupMemberInfo
import com.kurban.xuehuaim.sdk.model.Message
import com.kurban.xuehuaim.sdk.model.MessageEntity
import com.kurban.xuehuaim.sdk.model.MomentComment
import com.kurban.xuehuaim.sdk.model.MomentCommentWithUser
import com.kurban.xuehuaim.sdk.model.MomentInfo
import com.kurban.xuehuaim.sdk.model.MomentLike
import com.kurban.xuehuaim.sdk.model.PictureElem
import com.kurban.xuehuaim.sdk.model.PictureInfo
import com.kurban.xuehuaim.sdk.model.QuoteElem
import com.kurban.xuehuaim.sdk.model.RedPacketElem
import com.kurban.xuehuaim.sdk.model.SoundElem
import com.kurban.xuehuaim.sdk.model.UserFullInfo
import com.kurban.xuehuaim.sdk.model.UserInfo
import com.kurban.xuehuaim.sdk.model.UserStatusInfo
import com.kurban.xuehuaim.sdk.model.VideoElem
import com.kurban.xuehuaim.sdk.network.http.ImApiService
import com.kurban.xuehuaim.sdk.network.http.LoginUserIdProvider
import com.kurban.xuehuaim.sdk.network.http.applicationPlatformName
import com.kurban.xuehuaim.sdk.platform.FileSystem
import com.kurban.xuehuaim.sdk.platform.ioDispatcher
import com.kurban.xuehuaim.sdk.util.ClientMsgIdGenerator
import com.kurban.xuehuaim.sdk.util.ConversationMessageUpdater
import com.kurban.xuehuaim.sdk.util.OpenImUtils
import com.kurban.xuehuaim.sdk.util.withParsedContent
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

internal suspend fun GroupManager.getJoinedGroupListPage(
    apiService: ImApiService,
    loginUserId: LoginUserIdProvider,
    pageNumber: Int,
    pageSize: Int,
) = withContext(ioDispatcher) {
    apiService.getJoinedGroupListPage(loginUserId.requireUserId(), pageNumber, pageSize)
}

internal suspend fun GroupManager.isJoinedGroup(
    apiService: ImApiService,
    loginUserId: LoginUserIdProvider,
    groupId: String,
): Boolean = withContext(ioDispatcher) {
    apiService.getJoinedGroupList(loginUserId.requireUserId())
        .any { it.groupID == groupId }
}

internal suspend fun GroupManager.searchGroups(
    apiService: ImApiService,
    keyword: String,
): List<GroupInfo> = withContext(ioDispatcher) {
    apiService.getGroupsInfo(listOf(keyword)).ifEmpty {
        apiService.getJoinedGroupList("").filter {
            it.groupName?.contains(keyword, ignoreCase = true) == true
        }
    }
}

internal suspend fun GroupManager.searchGroupMembers(
    apiService: ImApiService,
    groupId: String,
    keyword: String,
): List<GroupMemberInfo> = withContext(ioDispatcher) {
    apiService.getGroupMembers(groupId).filter {
        it.nickname?.contains(keyword, ignoreCase = true) == true ||
            it.userID.contains(keyword, ignoreCase = true)
    }
}

internal suspend fun GroupManager.getGroupMembersInfo(
    apiService: ImApiService,
    groupId: String,
    userIds: List<String>,
) = withContext(ioDispatcher) { apiService.getGroupMembersInfo(groupId, userIds) }

internal suspend fun GroupManager.getGroupOwnerAndAdmin(
    apiService: ImApiService,
    groupId: String,
): List<GroupMemberInfo> = withContext(ioDispatcher) {
    apiService.getGroupMembers(groupId).filter {
        it.roleLevel == GroupRoleLevel.OWNER || it.roleLevel == GroupRoleLevel.ADMIN
    }
}

internal suspend fun GroupManager.setGroupMemberInfo(
    apiService: ImApiService,
    groupId: String,
    userId: String,
    nickname: String? = null,
    faceURL: String? = null,
) = withContext(ioDispatcher) {
    apiService.setGroupMemberInfo(groupId, userId, nickname, faceURL)
}

internal suspend fun GroupManager.transferGroupOwner(
    apiService: ImApiService,
    groupId: String,
    newOwnerUserId: String,
) = withContext(ioDispatcher) {
    apiService.transferGroup(groupId, newOwnerUserId)
}

internal suspend fun GroupManager.changeGroupMute(
    apiService: ImApiService,
    groupId: String,
    isMute: Boolean,
) = withContext(ioDispatcher) {
    if (isMute) apiService.muteGroup(groupId) else apiService.cancelMuteGroup(groupId)
}

internal suspend fun GroupManager.changeGroupMemberMute(
    apiService: ImApiService,
    groupId: String,
    userId: String,
    mutedSeconds: Long,
) = withContext(ioDispatcher) {
    if (mutedSeconds > 0) {
        apiService.muteGroupMember(groupId, userId, mutedSeconds)
    } else {
        apiService.cancelMuteGroupMember(groupId, userId)
    }
}
