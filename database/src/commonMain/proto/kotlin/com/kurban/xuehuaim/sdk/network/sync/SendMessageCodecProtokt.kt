package com.kurban.xuehuaim.sdk.network.sync

import protokt.v1.Bytes
import protokt.v1.openim.sdkws.MsgData
import protokt.v1.openim.sdkws.UserSendMsgResp

internal fun encodeSendMsgReqProtokt(data: SendMsgReqData): ByteArray {
    val msgOptions = buildMap {
        putAll(data.options)
        if (!data.offlinePush) put("offlinePush", false)
        if (data.isOnlineOnly) {
            put("history", false)
            put("persistent", false)
            put("offlinePush", false)
            put("unreadCount", false)
        }
    }
    return MsgData {
        sendID = data.sendID
        recvID = data.recvID
        groupID = data.groupID
        clientMsgID = data.clientMsgID
        senderPlatformID = data.senderPlatformID
        senderNickname = data.senderNickname
        senderFaceURL = data.senderFaceURL
        sessionType = data.sessionType
        msgFrom = data.msgFrom
        contentType = data.contentType
        content = Bytes.from(data.contentBytes)
        createTime = data.createTime
        atUserIDList = data.atUserIDList
        options = msgOptions
    }.serialize()
}

internal fun decodeUserSendMsgRespProtokt(data: ByteArray): UserSendMsgRespData? = try {
    val resp = UserSendMsgResp.deserialize(data)
    UserSendMsgRespData(
        serverMsgID = resp.serverMsgID,
        clientMsgID = resp.clientMsgID,
        sendTime = resp.sendTime,
    )
} catch (_: Exception) {
    null
}
