package com.kurban.xuehuaim.sdk.util

import com.kurban.xuehuaim.sdk.model.ConversationInfo

fun mergeConversation(existing: ConversationInfo, incoming: ConversationInfo): ConversationInfo {
    val latestMsg = (incoming.latestMsg ?: existing.latestMsg)?.withParsedContent()
    return incoming.copy(
        showName = incoming.showName?.takeIf { it.isNotBlank() } ?: existing.showName,
        faceURL = incoming.faceURL?.takeIf { it.isNotBlank() } ?: existing.faceURL,
        userID = incoming.userID?.takeIf { it.isNotBlank() } ?: existing.userID,
        groupID = incoming.groupID?.takeIf { it.isNotBlank() } ?: existing.groupID,
        latestMsg = latestMsg,
        latestMsgSendTime = incoming.latestMsgSendTime ?: existing.latestMsgSendTime,
    )
}

fun ConversationInfo.withParsedLatestMsg(): ConversationInfo {
    val parsed = latestMsg?.withParsedContent() ?: return this
    return if (parsed === latestMsg) this else copy(latestMsg = parsed)
}
