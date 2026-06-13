package com.kurban.xuehuaim.sdk.network.sync

import openim.sdkws.PushMessages

internal fun decodePushMessages(data: ByteArray): PullMsgResp? = try {
    val pushMessages = PushMessages.ADAPTER.decode(data)
    decodePullMsgsContent(pushMessages.toPullMsgResp())
} catch (_: Exception) {
    null
}
