package com.kurban.xuehuaim.sdk.network.sync

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Serializable
internal data class PullMsgBySeqsReq(
    @SerialName("userID") val userID: String,
    @SerialName("seqRanges") val seqRanges: List<SeqRange> = emptyList(),
    @SerialName("order") val order: Int = 0,
)

@Serializable
internal data class SeqRange(
    @SerialName("conversationID") val conversationID: String,
    @SerialName("begin") val begin: Long,
    @SerialName("end") val end: Long,
    @SerialName("num") val num: Long? = null,
)

@Serializable
internal data class PullMsgResp(
    @SerialName("msgs") val msgs: Map<String, MsgList> = emptyMap(),
    @SerialName("notificationMsgs") val notificationMsgs: Map<String, MsgList> = emptyMap(),
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class MsgList(
    @JsonNames("msgs", "Msgs") val msgs: List<WsMsgData> = emptyList(),
)

@Serializable
internal data class WsMsgData(
    @SerialName("clientMsgID") val clientMsgID: String,
    @SerialName("serverMsgID") val serverMsgID: String? = null,
    @SerialName("sendID") val sendID: String? = null,
    @SerialName("recvID") val recvID: String? = null,
    @SerialName("groupID") val groupID: String? = null,
    @SerialName("contentType") val contentType: Int? = null,
    @SerialName("content") val content: String? = null,
    @SerialName("seq") val seq: Long = 0,
    @SerialName("sendTime") val sendTime: Long? = null,
    @SerialName("createTime") val createTime: Long? = null,
    @SerialName("sessionType") val sessionType: Int? = null,
    @SerialName("senderNickname") val senderNickname: String? = null,
    @SerialName("senderFaceURL") val senderFaceURL: String? = null,
)

@OptIn(ExperimentalEncodingApi::class)
internal fun decodePullMsgsContent(pullResp: PullMsgResp): PullMsgResp = pullResp.copy(
    msgs = pullResp.msgs.mapValues { (_, list) -> list.copy(msgs = list.msgs.map(::decodeWsMsgContent)) },
    notificationMsgs = pullResp.notificationMsgs.mapValues { (_, list) ->
        list.copy(msgs = list.msgs.map(::decodeWsMsgContent))
    },
)

@OptIn(ExperimentalEncodingApi::class)
private fun decodeWsMsgContent(msg: WsMsgData): WsMsgData {
    val content = msg.content ?: return msg
    if (content.isEmpty()) return msg
    val first = content.first().code
    if (first == '{'.code || first == '['.code) return msg
    return try {
        msg.copy(content = Base64.decode(content).decodeToString())
    } catch (_: Exception) {
        msg
    }
}
