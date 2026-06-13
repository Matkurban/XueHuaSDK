package com.kurban.xuehuaim.sdk.sync

import com.kurban.xuehuaim.sdk.model.ConversationInfo
import com.kurban.xuehuaim.sdk.network.http.ConversationsHasReadAndMaxSeqResp
import com.kurban.xuehuaim.sdk.network.http.IncrementalConversationResp

internal interface ConversationSyncApi {
    suspend fun getIncrementalConversations(
        userID: String,
        version: Int,
        versionID: String,
    ): IncrementalConversationResp

    suspend fun getAllConversations(ownerUserID: String): List<ConversationInfo>

    suspend fun getFullConversationIDs(userID: String): List<String>

    suspend fun getConversations(
        ownerUserID: String,
        conversationIDs: List<String>,
    ): List<ConversationInfo>

    suspend fun getConversationsHasReadAndMaxSeq(
        userID: String,
        conversationIDs: List<String>,
    ): ConversationsHasReadAndMaxSeqResp
}
