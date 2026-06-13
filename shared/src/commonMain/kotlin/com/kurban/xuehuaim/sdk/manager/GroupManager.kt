package com.kurban.xuehuaim.sdk.manager

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.model.GroupApplicationInfo
import com.kurban.xuehuaim.sdk.model.GroupInfo
import com.kurban.xuehuaim.sdk.model.GroupMemberInfo
import com.kurban.xuehuaim.sdk.network.http.ImApiService
import com.kurban.xuehuaim.sdk.network.http.LoginUserIdProvider
import com.kurban.xuehuaim.sdk.platform.ioDispatcher
import com.kurban.xuehuaim.sdk.sync.GroupSync
import com.kurban.xuehuaim.sdk.sync.VersionedListPager
import kotlinx.coroutines.withContext


class GroupManager internal constructor(
    private val apiService: ImApiService,
    private val databaseService: DatabaseService,
    private val eventEmitter: SdkEventEmitter,
    private val loginUserId: LoginUserIdProvider,
) {
    suspend fun getJoinedGroupList(): List<GroupInfo> = withContext(ioDispatcher) {
        databaseService.getAllGroups()
    }

    suspend fun createGroup(groupName: String, memberUserIds: List<String>): GroupInfo =
        withContext(ioDispatcher) {
            val group = apiService.createGroup(groupName, memberUserIds)
            databaseService.insertOrReplaceGroup(group)
            eventEmitter.emitGroup(com.kurban.xuehuaim.sdk.event.GroupEvent.GroupInfoChanged(group))
            group
        }

    suspend fun dismissGroup(groupId: String) = withContext(ioDispatcher) {
        apiService.dismissGroup(groupId)
        databaseService.deleteGroupMembers(groupId)
        databaseService.deleteGroup(groupId)
        eventEmitter.emitGroup(com.kurban.xuehuaim.sdk.event.GroupEvent.GroupDismissed(groupId))
    }

    suspend fun quitGroup(groupId: String) = withContext(ioDispatcher) {
        val userId = loginUserId.requireUserId()
        apiService.quitGroup(userId, groupId)
        GroupSync.syncJoinedGroups(apiService, databaseService, eventEmitter, userId)
        eventEmitter.emitGroup(com.kurban.xuehuaim.sdk.event.GroupEvent.GroupDismissed(groupId))
    }

    suspend fun getGroupsInfo(groupIds: List<String>): List<GroupInfo> =
        apiService.getGroupsInfo(groupIds)

    suspend fun setGroupInfo(groupInfo: GroupInfo) = withContext(ioDispatcher) {
        apiService.setGroupInfoEx(groupInfo)
        databaseService.insertOrReplaceGroup(groupInfo)
        eventEmitter.emitGroup(com.kurban.xuehuaim.sdk.event.GroupEvent.GroupInfoChanged(groupInfo))
    }

    suspend fun getGroupMemberList(
        groupID: String,
        filter: Int = 0,
        offset: Int = 0,
        count: Int = 40,
    ): List<GroupMemberInfo> = withContext(ioDispatcher) {
        GroupSync.ensureGroupMembersSynced(apiService, databaseService, groupID)
        val page = databaseService.getGroupMembersPage(groupID, offset, count, filter)
        if (page.isNotEmpty()) return@withContext page
        apiService.getGroupMembers(groupID).let { members ->
            databaseService.batchUpsertGroupMembers(members)
            members.drop(offset).take(count).let { fallback ->
                if (filter <= 0) fallback else fallback.filter { it.roleLevel?.value == filter }
            }
        }
    }

    suspend fun inviteUserToGroup(
        groupID: String,
        userIDList: List<String>,
        reason: String? = null,
    ) = withContext(ioDispatcher) {
        apiService.inviteToGroup(groupID, userIDList)
        GroupSync.syncGroupInfoAndMembers(apiService, databaseService, groupID)
    }

    suspend fun kickGroupMember(
        groupID: String,
        userIDList: List<String>,
        reason: String? = null,
    ) = withContext(ioDispatcher) {
        apiService.kickGroupMember(groupID, userIDList, reason)
        GroupSync.syncGroupInfoAndMembers(apiService, databaseService, groupID)
    }

    suspend fun getGroupApplicationListAsRecipient(
        pageNumber: Int = 1,
        pageSize: Int = 100,
    ): List<GroupApplicationInfo> =
        apiService.getRecvGroupApplicationList(loginUserId.requireUserId(), pageNumber, pageSize)

    suspend fun getGroupApplicationListAsApplicant(
        pageNumber: Int = 1,
        pageSize: Int = 100,
    ): List<GroupApplicationInfo> =
        apiService.getSendGroupApplicationList(loginUserId.requireUserId(), pageNumber, pageSize)

    suspend fun joinGroup(
        groupId: String,
        reqMessage: String = "",
        joinSource: Int = 3,
        inviterUserID: String = "",
        ex: String = "",
    ) = withContext(ioDispatcher) {
        apiService.joinGroup(groupId, reqMessage, joinSource, inviterUserID, ex)
        GroupSync.syncJoinedGroups(
            apiService,
            databaseService,
            eventEmitter,
            loginUserId.requireUserId(),
        )
    }

    suspend fun acceptGroupApplication(
        groupId: String,
        userId: String,
        handleMsg: String = "",
    ) = withContext(ioDispatcher) {
        apiService.groupApplicationResponse(groupId, userId, handleMsg, handleResult = 1)
        GroupSync.syncGroupInfoAndMembers(apiService, databaseService, groupId)
    }

    suspend fun refuseGroupApplication(
        groupId: String,
        userId: String,
        handleMsg: String = "",
    ) = withContext(ioDispatcher) {
        apiService.groupApplicationResponse(groupId, userId, handleMsg, handleResult = -1)
    }

    suspend fun getGroupApplicationUnhandledCount(time: Long = 0): Int =
        withContext(ioDispatcher) {
            apiService.getGroupApplicationUnhandledCount(loginUserId.requireUserId(), time)
        }

    suspend fun getJoinedGroupListPage(
        offset: Int = 0,
        count: Int = 40,
    ): List<GroupInfo> = withContext(ioDispatcher) {
        VersionedListPager.fetchJoinedGroupsPage(
            databaseService = databaseService,
            apiService = apiService,
            userId = loginUserId.requireUserId(),
            offset = offset,
            count = count,
        )
    }

    suspend fun isJoinedGroup(groupId: String): Boolean = withContext(ioDispatcher) {
        databaseService.getGroupsByGroupIds(listOf(groupId)).isNotEmpty()
    }

    suspend fun searchGroups(keyword: String): List<GroupInfo> =
        searchGroups(apiService, keyword)

    suspend fun searchGroupMembers(groupId: String, keyword: String): List<GroupMemberInfo> =
        searchGroupMembers(apiService, groupId, keyword)

    suspend fun getGroupMembersInfo(groupId: String, userIds: List<String>): List<GroupMemberInfo> =
        getGroupMembersInfo(apiService, groupId, userIds)

    suspend fun getGroupOwnerAndAdmin(groupId: String): List<GroupMemberInfo> =
        getGroupOwnerAndAdmin(apiService, groupId)

    suspend fun setGroupMemberInfo(
        groupId: String,
        userId: String,
        nickname: String? = null,
        faceURL: String? = null,
    ) = setGroupMemberInfo(apiService, groupId, userId, nickname, faceURL)

    suspend fun transferGroupOwner(groupId: String, newOwnerUserId: String) =
        transferGroupOwner(apiService, groupId, newOwnerUserId)

    suspend fun getGroupMemberListByJoinTime(
        groupID: String,
        offset: Int = 0,
        count: Int = 0,
        joinTimeBegin: Long = 0,
        joinTimeEnd: Long = 0,
        filterUserIDList: List<String> = emptyList(),
    ): List<GroupMemberInfo> = withContext(ioDispatcher) {
        GroupSync.ensureGroupMembersSynced(apiService, databaseService, groupID)
        val pageCount = if (count == 0) 40 else count
        var members = databaseService.getGroupMembersPage(groupID, offset, pageCount)
        if (joinTimeBegin > 0 || joinTimeEnd > 0) {
            members = members.filter { member ->
                val joinTime = member.joinTime ?: 0
                (joinTimeBegin <= 0 || joinTime >= joinTimeBegin) &&
                        (joinTimeEnd <= 0 || joinTime <= joinTimeEnd)
            }
        }
        if (filterUserIDList.isNotEmpty()) {
            members = members.filter { it.userID !in filterUserIDList }
        }
        members
    }

    suspend fun getUsersInGroup(groupID: String, userIDList: List<String>): List<String> =
        withContext(ioDispatcher) {
            getGroupMembersInfo(apiService, groupID, userIDList).map { it.userID }
        }

    suspend fun changeGroupMute(groupId: String, isMute: Boolean) =
        changeGroupMute(apiService, groupId, isMute)

    suspend fun changeGroupMemberMute(groupId: String, userId: String, mutedSeconds: Long) =
        changeGroupMemberMute(apiService, groupId, userId, mutedSeconds)
}
