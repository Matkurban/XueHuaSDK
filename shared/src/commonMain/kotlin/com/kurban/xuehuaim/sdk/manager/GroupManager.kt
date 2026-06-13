package com.kurban.xuehuaim.sdk.manager

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.db.SendingMessage
import com.kurban.xuehuaim.sdk.enum.ConnectionState
import com.kurban.xuehuaim.sdk.enum.ConversationType
import com.kurban.xuehuaim.sdk.enum.GroupAtType
import com.kurban.xuehuaim.sdk.enum.MessageStatus
import com.kurban.xuehuaim.sdk.enum.MessageType
import com.kurban.xuehuaim.sdk.enum.ReceiveMessageOpt
import com.kurban.xuehuaim.sdk.enum.SdkErrorCode
import com.kurban.xuehuaim.sdk.exception.XueHuaException
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.model.AppealInfo
import com.kurban.xuehuaim.sdk.model.ApplicationVersionInfo
import com.kurban.xuehuaim.sdk.model.AtTextElem
import com.kurban.xuehuaim.sdk.model.AtUserInfo
import com.kurban.xuehuaim.sdk.model.AuthCacheData
import com.kurban.xuehuaim.sdk.model.BlacklistInfo
import com.kurban.xuehuaim.sdk.model.ConversationInfo
import com.kurban.xuehuaim.sdk.model.ConversationReq
import com.kurban.xuehuaim.sdk.model.CustomElem
import com.kurban.xuehuaim.sdk.model.FavoriteItem
import com.kurban.xuehuaim.sdk.model.FileElem
import com.kurban.xuehuaim.sdk.model.FriendApplicationInfo
import com.kurban.xuehuaim.sdk.model.FriendInfo
import com.kurban.xuehuaim.sdk.model.GroupApplicationInfo
import com.kurban.xuehuaim.sdk.model.GroupInfo
import com.kurban.xuehuaim.sdk.model.GroupMemberInfo
import com.kurban.xuehuaim.sdk.model.MergeElem
import com.kurban.xuehuaim.sdk.model.Message
import com.kurban.xuehuaim.sdk.model.MomentCommentWithUser
import com.kurban.xuehuaim.sdk.model.MomentInfo
import com.kurban.xuehuaim.sdk.model.MomentListResponse
import com.kurban.xuehuaim.sdk.model.PictureElem
import com.kurban.xuehuaim.sdk.model.PictureInfo
import com.kurban.xuehuaim.sdk.model.PointsTransaction
import com.kurban.xuehuaim.sdk.model.QuoteElem
import com.kurban.xuehuaim.sdk.model.RedPacketDetail
import com.kurban.xuehuaim.sdk.model.SearchParams
import com.kurban.xuehuaim.sdk.model.SendRedPacketRequest
import com.kurban.xuehuaim.sdk.model.ReportInfo
import com.kurban.xuehuaim.sdk.model.SearchResult
import com.kurban.xuehuaim.sdk.model.SoundElem
import com.kurban.xuehuaim.sdk.model.TextElem
import com.kurban.xuehuaim.sdk.model.UserFullInfo
import com.kurban.xuehuaim.sdk.model.UserInfo
import com.kurban.xuehuaim.sdk.model.UserStatusInfo
import com.kurban.xuehuaim.sdk.model.VideoElem
import com.kurban.xuehuaim.sdk.network.http.ImApiService
import com.kurban.xuehuaim.sdk.network.http.LoginUserIdProvider
import com.kurban.xuehuaim.sdk.network.notify.NotificationDispatcher
import com.kurban.xuehuaim.sdk.network.sync.MsgSyncer
import com.kurban.xuehuaim.sdk.network.sync.SendMsgReqData
import com.kurban.xuehuaim.sdk.network.sync.decodeUserSendMsgResp
import com.kurban.xuehuaim.sdk.network.sync.encodeSendMsgReq
import com.kurban.xuehuaim.sdk.network.ws.WebSocketService
import com.kurban.xuehuaim.sdk.network.ws.WsIdentifier
import com.kurban.xuehuaim.sdk.network.ws.WsRequest
import com.kurban.xuehuaim.sdk.platform.currentPlatform
import com.kurban.xuehuaim.sdk.platform.FileSystem
import com.kurban.xuehuaim.sdk.platform.ioDispatcher
import com.kurban.xuehuaim.sdk.platform.sdkScope
import com.kurban.xuehuaim.sdk.sync.FavoriteSync
import com.kurban.xuehuaim.sdk.sync.FriendSync
import com.kurban.xuehuaim.sdk.sync.GroupSync
import com.kurban.xuehuaim.sdk.sync.MessageDisplayEnricher
import com.kurban.xuehuaim.sdk.sync.MomentSync
import com.kurban.xuehuaim.sdk.util.ClientMsgIdGenerator
import com.kurban.xuehuaim.sdk.util.ConversationMessageUpdater
import com.kurban.xuehuaim.sdk.util.OpenImUtils
import com.kurban.xuehuaim.sdk.util.md5Hex
import com.kurban.xuehuaim.sdk.util.withParsedContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds


class GroupManager internal constructor(
    private val apiService: ImApiService,
    private val databaseService: DatabaseService,
    private val eventEmitter: SdkEventEmitter,
    private val loginUserId: LoginUserIdProvider,
) {
    suspend fun getJoinedGroupList(): List<GroupInfo> = withContext(ioDispatcher) {
        val userId = loginUserId.requireUserId()
        var groups = databaseService.getAllGroups()
        if (groups.isEmpty()) {
            GroupSync.syncJoinedGroups(apiService, databaseService, eventEmitter, userId)
            groups = databaseService.getAllGroups()
        }
        if (groups.isEmpty()) {
            groups = apiService.getJoinedGroupList(userId)
            databaseService.batchUpsertGroups(groups)
        }
        groups
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

    suspend fun getJoinedGroupListPage(pageNumber: Int = 1, pageSize: Int = 40): List<GroupInfo> =
        getJoinedGroupListPage(apiService, loginUserId, pageNumber, pageSize)

    suspend fun isJoinedGroup(groupId: String): Boolean =
        isJoinedGroup(apiService, loginUserId, groupId)

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
