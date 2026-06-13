package com.kurban.xuehuaim.sdk.network.sync

internal expect fun decodePushMessages(data: ByteArray): PullMsgResp?
