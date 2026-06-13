package com.kurban.xuehuaim.sdk.manager

import com.kurban.xuehuaim.sdk.enum.GroupRoleLevel
import com.kurban.xuehuaim.sdk.model.GroupInfo
import com.kurban.xuehuaim.sdk.model.GroupMemberInfo
import com.kurban.xuehuaim.sdk.network.http.ImApiService
import com.kurban.xuehuaim.sdk.network.http.LoginUserIdProvider
import com.kurban.xuehuaim.sdk.platform.ioDispatcher
import kotlinx.coroutines.withContext

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
