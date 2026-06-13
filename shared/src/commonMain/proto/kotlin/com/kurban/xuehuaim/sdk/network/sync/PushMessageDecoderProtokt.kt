package com.kurban.xuehuaim.sdk.network.sync

import protokt.v1.openim.sdkws.PushMessages

internal fun decodePushMessagesProtokt(data: ByteArray): PullMsgResp? = try {
    val pushMessages = PushMessages.deserialize(data)
    decodePullMsgsContent(pushMessages.toPullMsgResp())
} catch (_: Exception) {
    null
}
