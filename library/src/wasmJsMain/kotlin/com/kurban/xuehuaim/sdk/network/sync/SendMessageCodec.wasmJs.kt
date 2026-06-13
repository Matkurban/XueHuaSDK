package com.kurban.xuehuaim.sdk.network.sync

internal actual fun encodeSendMsgReq(data: SendMsgReqData): ByteArray =
    throw UnsupportedOperationException("WebSocket send is not supported on Wasm in this build")

internal actual fun decodeUserSendMsgResp(data: ByteArray): UserSendMsgRespData? = null
