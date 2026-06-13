package com.kurban.xuehuaim.sdk.sync

import com.kurban.xuehuaim.sdk.model.GroupInfo
import com.kurban.xuehuaim.sdk.network.http.IncrementalJoinGroupResp

internal interface GroupSyncApi {
    suspend fun getIncrementalJoinGroup(
        userID: String,
        version: Int,
        versionID: String,
    ): IncrementalJoinGroupResp

    suspend fun getJoinedGroupList(fromUserID: String, pageSize: Int = 100): List<GroupInfo>

    suspend fun getFullJoinGroupIDs(userID: String): List<String>
}
