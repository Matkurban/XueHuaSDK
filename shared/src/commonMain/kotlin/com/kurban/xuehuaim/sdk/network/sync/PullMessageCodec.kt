package com.kurban.xuehuaim.sdk.network.sync

import openim.msg.ConversationSeqs
import openim.msg.GetSeqMessageReq
import openim.msg.GetSeqMessageResp
import openim.sdkws.PullMessageBySeqsReq
import openim.sdkws.PullMessageBySeqsResp
import openim.sdkws.PullOrder
import openim.sdkws.SeqRange

internal fun encodePullMessageBySeqsReq(
    userID: String,
    seqRanges: List<com.kurban.xuehuaim.sdk.network.sync.SeqRange>,
    order: Int,
): ByteArray = PullMessageBySeqsReq(
    userID = userID,
    seqRanges = seqRanges.map { range ->
        SeqRange(
            conversationID = range.conversationID,
            begin = range.begin,
            end = range.end,
            num = range.num ?: 0L,
        )
    },
    order = if (order == 0) PullOrder.PullOrderAsc else PullOrder.PullOrderDesc,
).encode()

internal fun decodePullMessageBySeqsResp(data: ByteArray): PullMsgResp? = try {
    PullMessageBySeqsResp.ADAPTER.decode(data).toPullMsgResp()
} catch (_: Exception) {
    null
}

internal fun encodeGetSeqMessageReq(
    userID: String,
    conversationID: String,
    seqs: List<Long>,
    order: Int,
): ByteArray = GetSeqMessageReq(
    userID = userID,
    conversations = listOf(
        ConversationSeqs(
            conversationID = conversationID,
            seqs = seqs,
        ),
    ),
    order = if (order == 0) PullOrder.PullOrderAsc else PullOrder.PullOrderDesc,
).encode()

internal fun decodeGetSeqMessageResp(data: ByteArray): PullMsgResp? = try {
    GetSeqMessageResp.ADAPTER.decode(data).toPullMsgResp()
} catch (_: Exception) {
    null
}

private fun PullMessageBySeqsResp.toPullMsgResp(): PullMsgResp = PullMsgResp(
    msgs = msgs.mapValues { (_, pullMsgs) -> pullMsgs.toMsgList() },
    notificationMsgs = notificationMsgs.mapValues { (_, pullMsgs) -> pullMsgs.toMsgList() },
)

private fun GetSeqMessageResp.toPullMsgResp(): PullMsgResp = PullMsgResp(
    msgs = msgs.mapValues { (_, pullMsgs) -> pullMsgs.toMsgList() },
    notificationMsgs = notificationMsgs.mapValues { (_, pullMsgs) -> pullMsgs.toMsgList() },
)

private fun openim.sdkws.PullMsgs.toMsgList(): MsgList = MsgList(
    msgs = Msgs.map { it.toWsMsgData() },
    isEnd = isEnd,
    endSeq = endSeq,
)
