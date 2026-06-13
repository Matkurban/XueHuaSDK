package com.kurban.xuehuaim.sdk.util

import com.kurban.xuehuaim.sdk.model.ConversationInfo

fun ConversationInfo.normalizedForStorage(): ConversationInfo {
    val sendTime = latestMsgSendTime
        ?: latestMsg?.sendTime
        ?: latestMsg?.createTime
        ?: 0L
    return if (sendTime > 0 && latestMsgSendTime != sendTime) {
        copy(latestMsgSendTime = sendTime)
    } else {
        this
    }
}
