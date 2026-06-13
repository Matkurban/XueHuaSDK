package com.kurban.xuehuaim.sdk.network.sync

internal actual fun decodePushMessages(data: ByteArray): PullMsgResp? = try {
    decodePullMsgsContent(PushMessagesWireDecoder.decode(data))
} catch (_: Exception) {
    null
}
