package com.kurban.xuehuaim.sdk.network.ws

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object WsIdentifier {
    const val GET_NEWEST_SEQ = 1001

    const val PULL_MSG_BY_RANGE = 1002
    const val SEND_MSG = 1003
    const val SEND_SIGNAL_MSG = 1004

    const val PULL_MSG_BY_SEQ_LIST = 1005

    const val GET_CONV_MAX_READ_SEQ = 1006

    const val PULL_CONV_LAST_MESSAGE = 1007
    const val PUSH_MSG = 2001
    const val KICK_ONLINE_MSG = 2002
    const val LOGOUT_MSG = 2003

    const val SET_BACKGROUND_STATUS = 2004
    const val WS_SUB_USER_ONLINE_STATUS = 2005
}

data class WsRequest(
    val reqIdentifier: Int,
    val token: String = "",
    val sendID: String = "",
    val operationID: String = "",
    var msgIncr: String = "",
    val data: ByteArray = ByteArray(0),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as WsRequest

        if (reqIdentifier != other.reqIdentifier) return false
        if (token != other.token) return false
        if (sendID != other.sendID) return false
        if (operationID != other.operationID) return false
        if (msgIncr != other.msgIncr) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = reqIdentifier
        result = 31 * result + token.hashCode()
        result = 31 * result + sendID.hashCode()
        result = 31 * result + operationID.hashCode()
        result = 31 * result + msgIncr.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

data class WsResponse(
    val reqIdentifier: Int = 0,
    val errCode: Int = 0,
    val errMsg: String = "",
    val msgIncr: String = "",
    val operationID: String = "",
    val data: ByteArray = ByteArray(0),
) {
    val isSuccess: Boolean get() = errCode == 0
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as WsResponse

        if (reqIdentifier != other.reqIdentifier) return false
        if (errCode != other.errCode) return false
        if (errMsg != other.errMsg) return false
        if (msgIncr != other.msgIncr) return false
        if (operationID != other.operationID) return false
        if (!data.contentEquals(other.data)) return false
        if (isSuccess != other.isSuccess) return false

        return true
    }

    override fun hashCode(): Int {
        var result = reqIdentifier
        result = 31 * result + errCode
        result = 31 * result + errMsg.hashCode()
        result = 31 * result + msgIncr.hashCode()
        result = 31 * result + operationID.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + isSuccess.hashCode()
        return result
    }
}

@Serializable
internal data class WsRequestJson(
    @SerialName("reqIdentifier") val reqIdentifier: Int,
    @SerialName("token") val token: String = "",
    @SerialName("sendID") val sendID: String = "",
    @SerialName("operationID") val operationID: String = "",
    @SerialName("msgIncr") val msgIncr: String = "",
    @SerialName("data") val data: String = "",
)

@Serializable
internal data class WsResponseJson(
    @SerialName("reqIdentifier") val reqIdentifier: Int = 0,
    @SerialName("errCode") val errCode: Int = 0,
    @SerialName("errMsg") val errMsg: String = "",
    @SerialName("msgIncr") val msgIncr: String = "",
    @SerialName("operationID") val operationID: String = "",
    @SerialName("data") val data: String? = null,
)
