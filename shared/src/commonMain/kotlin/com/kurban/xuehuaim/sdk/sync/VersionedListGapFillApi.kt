package com.kurban.xuehuaim.sdk.sync

import com.kurban.xuehuaim.sdk.model.FriendInfo
import com.kurban.xuehuaim.sdk.model.GroupInfo

internal interface VersionedListGapFillApi {
    suspend fun getDesignatedFriends(ownerUserID: String, friendUserIDs: List<String>): List<FriendInfo>
    suspend fun getGroupsInfo(groupIDs: List<String>): List<GroupInfo>
}
