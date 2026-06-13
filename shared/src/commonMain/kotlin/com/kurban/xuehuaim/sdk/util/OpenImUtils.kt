package com.kurban.xuehuaim.sdk.util

import com.kurban.xuehuaim.sdk.enum.ConversationType

object OpenImUtils {
    fun genSingleConversationID(userID1: String, userID2: String): String {
        val sorted = listOf(userID1, userID2).sorted()
        return "si_${sorted[0]}_${sorted[1]}"
    }

    fun genGroupConversationID(groupID: String): String = "sg_$groupID"

    fun genNotificationConversationID(userID1: String, userID2: String): String {
        val sorted = listOf(userID1, userID2).sorted()
        return "sn_${sorted[0]}_${sorted[1]}"
    }

    fun getConversationIDBySessionType(
        selfUserId: String,
        sourceID: String,
        sessionType: Int,
    ): String = when (sessionType) {
        ConversationType.SINGLE.value -> genSingleConversationID(selfUserId, sourceID)
        ConversationType.SUPER_GROUP.value -> genGroupConversationID(sourceID)
        else -> genNotificationConversationID(selfUserId, sourceID)
    }

    fun generateSpaceName(userID: String): String = "kurban_open_im_$userID"
}

object CacheKey {
    const val LOGIN_AUTH_DATA = "loginAuthData"
}
