package com.kurban.xuehuaim.sdk.network.sync

internal actual fun encodeSendMsgReq(data: SendMsgReqData): ByteArray =
    encodeSendMsgReqProtokt(data)

internal actual fun decodeUserSendMsgResp(data: ByteArray): UserSendMsgRespData? =
    decodeUserSendMsgRespProtokt(data)
