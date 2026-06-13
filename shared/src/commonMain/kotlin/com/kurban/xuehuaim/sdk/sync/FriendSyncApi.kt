package com.kurban.xuehuaim.sdk.sync

import com.kurban.xuehuaim.sdk.model.FriendInfo
import com.kurban.xuehuaim.sdk.network.http.IncrementalFriendsResp

internal interface FriendSyncApi {
    suspend fun getIncrementalFriends(
        userID: String,
        version: Int,
        versionID: String,
    ): IncrementalFriendsResp

    suspend fun getFriendList(userID: String, pageSize: Int = 100): List<FriendInfo>

    suspend fun getFullFriendUserIDs(userID: String): List<String>
}
