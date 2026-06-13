package com.kurban.xuehuaim.sdk.network.sync

internal actual fun decodePushMessages(data: ByteArray): PullMsgResp? =
    decodePushMessagesProtokt(data)
