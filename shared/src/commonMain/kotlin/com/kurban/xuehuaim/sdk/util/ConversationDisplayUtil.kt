package com.kurban.xuehuaim.sdk.util

import com.kurban.xuehuaim.sdk.enum.ConversationType
import com.kurban.xuehuaim.sdk.model.ConversationInfo
import com.kurban.xuehuaim.sdk.model.FriendInfo
import com.kurban.xuehuaim.sdk.model.GroupInfo
import com.kurban.xuehuaim.sdk.model.UserInfo

fun conversationDisplayName(conversation: ConversationInfo): String {
    conversation.showName?.takeIf { it.isNotBlank() }?.let { return it }
    return conversation.conversationID
}

fun peerUserId(conversation: ConversationInfo, selfUserId: String): String? {
    conversation.userID?.takeIf { it.isNotBlank() && it != selfUserId }?.let { return it }
    if (conversation.conversationType == ConversationType.SINGLE) {
        return parsePeerUserIdFromConversationId(conversation.conversationID, selfUserId)
    }
    return null
}

fun parsePeerUserIdFromConversationId(conversationId: String, selfUserId: String): String? {
    if (!conversationId.startsWith("si_")) return null
    val parts = conversationId.removePrefix("si_").split("_").filter { it.isNotBlank() }
    return parts.firstOrNull { it != selfUserId } ?: parts.lastOrNull()
}

fun resolveGroupId(conversation: ConversationInfo): String? {
    conversation.groupID?.takeIf { it.isNotBlank() }?.let { return it }
    val id = conversation.conversationID
    if (id.startsWith("sg_")) {
        return id.removePrefix("sg_").takeIf { it.isNotBlank() }
    }
    return null
}

fun resolveSingleChatDisplay(
    conversation: ConversationInfo,
    friends: Map<String, FriendInfo>,
    users: Map<String, UserInfo>,
    selfUserId: String,
): ConversationInfo {
    if (!conversation.showName.isNullOrBlank()) return conversation
    val peerId = peerUserId(conversation, selfUserId) ?: return conversation
    val friend = friends[peerId]
    val user = users[peerId]
    val showName = friend?.getShowName()
        ?: user?.getShowName()
        ?: peerId
    val faceURL = friend?.faceURL?.takeIf { it.isNotBlank() }
        ?: user?.faceURL?.takeIf { it.isNotBlank() }
        ?: conversation.faceURL
    return conversation.copy(
        userID = peerId,
        showName = showName,
        faceURL = faceURL,
    )
}

fun resolveGroupChatDisplay(
    conversation: ConversationInfo,
    groups: Map<String, GroupInfo>,
): ConversationInfo {
    if (!conversation.showName.isNullOrBlank()) return conversation
    val groupId = resolveGroupId(conversation) ?: return conversation
    val group = groups[groupId] ?: return conversation
    return conversation.copy(
        groupID = groupId,
        showName = group.groupName?.takeIf { it.isNotBlank() } ?: conversation.showName,
        faceURL = group.faceURL?.takeIf { it.isNotBlank() } ?: conversation.faceURL,
    )
}
