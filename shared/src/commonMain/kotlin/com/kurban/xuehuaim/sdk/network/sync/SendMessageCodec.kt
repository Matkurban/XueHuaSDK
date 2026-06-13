package com.kurban.xuehuaim.sdk.network.sync

import okio.ByteString.Companion.toByteString
import openim.sdkws.MsgData
import openim.sdkws.UserSendMsgResp

internal data class SendMsgReqData(
    val sendID: String,
    val recvID: String,
    val groupID: String,
    val clientMsgID: String,
    val senderPlatformID: Int,
    val senderNickname: String,
    val senderFaceURL: String,
    val sessionType: Int,
    val msgFrom: Int,
    val contentType: Int,
    val contentBytes: ByteArray,
    val createTime: Long,
    val atUserIDList: List<String> = emptyList(),
    val options: Map<String, Boolean> = emptyMap(),
    val offlinePush: Boolean = true,
    val isOnlineOnly: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as SendMsgReqData

        if (senderPlatformID != other.senderPlatformID) return false
        if (sessionType != other.sessionType) return false
        if (msgFrom != other.msgFrom) return false
        if (contentType != other.contentType) return false
        if (createTime != other.createTime) return false
        if (offlinePush != other.offlinePush) return false
        if (isOnlineOnly != other.isOnlineOnly) return false
        if (sendID != other.sendID) return false
        if (recvID != other.recvID) return false
        if (groupID != other.groupID) return false
        if (clientMsgID != other.clientMsgID) return false
        if (senderNickname != other.senderNickname) return false
        if (senderFaceURL != other.senderFaceURL) return false
        if (!contentBytes.contentEquals(other.contentBytes)) return false
        if (atUserIDList != other.atUserIDList) return false
        if (options != other.options) return false

        return true
    }

    override fun hashCode(): Int {
        var result = senderPlatformID
        result = 31 * result + sessionType
        result = 31 * result + msgFrom
        result = 31 * result + contentType
        result = 31 * result + createTime.hashCode()
        result = 31 * result + offlinePush.hashCode()
        result = 31 * result + isOnlineOnly.hashCode()
        result = 31 * result + sendID.hashCode()
        result = 31 * result + recvID.hashCode()
        result = 31 * result + groupID.hashCode()
        result = 31 * result + clientMsgID.hashCode()
        result = 31 * result + senderNickname.hashCode()
        result = 31 * result + senderFaceURL.hashCode()
        result = 31 * result + contentBytes.contentHashCode()
        result = 31 * result + atUserIDList.hashCode()
        result = 31 * result + options.hashCode()
        return result
    }
}

internal data class UserSendMsgRespData(
    val serverMsgID: String,
    val clientMsgID: String,
    val sendTime: Long,
)

internal fun encodeSendMsgReq(data: SendMsgReqData): ByteArray {
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
    return MsgData.ADAPTER.encode(
        MsgData(
            sendID = data.sendID,
            recvID = data.recvID,
            groupID = data.groupID,
            clientMsgID = data.clientMsgID,
            senderPlatformID = data.senderPlatformID,
            senderNickname = data.senderNickname,
            senderFaceURL = data.senderFaceURL,
            sessionType = data.sessionType,
            msgFrom = data.msgFrom,
            contentType = data.contentType,
            content = data.contentBytes.toByteString(),
            createTime = data.createTime,
            atUserIDList = data.atUserIDList,
            options = msgOptions,
        ),
    )
}

internal fun decodeUserSendMsgResp(data: ByteArray): UserSendMsgRespData? = try {
    val resp = UserSendMsgResp.ADAPTER.decode(data)
    UserSendMsgRespData(
        serverMsgID = resp.serverMsgID,
        clientMsgID = resp.clientMsgID,
        sendTime = resp.sendTime,
    )
} catch (_: Exception) {
    null
}
