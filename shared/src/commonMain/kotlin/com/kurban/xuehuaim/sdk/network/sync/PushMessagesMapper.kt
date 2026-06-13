package com.kurban.xuehuaim.sdk.network.sync

import okio.ByteString
import openim.sdkws.MsgData
import openim.sdkws.PullMsgs
import openim.sdkws.PushMessages

internal fun PushMessages.toPullMsgResp(): PullMsgResp = PullMsgResp(
    msgs = msgs.mapValues { (_, pullMsgs) -> pullMsgs.toMsgList() },
    notificationMsgs = notificationMsgs.mapValues { (_, pullMsgs) -> pullMsgs.toMsgList() },
)

private fun PullMsgs.toMsgList(): MsgList = MsgList(
    msgs = Msgs.map { it.toWsMsgData() },
)

internal fun MsgData.toWsMsgData(): WsMsgData = WsMsgData(
    clientMsgID = clientMsgID,
    serverMsgID = serverMsgID.takeIf { it.isNotEmpty() },
    sendID = sendID.takeIf { it.isNotEmpty() },
    recvID = recvID.takeIf { it.isNotEmpty() },
    groupID = groupID.takeIf { it.isNotEmpty() },
    contentType = contentType.takeIf { it != 0 },
    content = content.toContentString(),
    seq = seq,
    sendTime = sendTime.takeIf { it != 0L },
    createTime = createTime.takeIf { it != 0L },
    sessionType = sessionType.takeIf { it != 0 },
    senderNickname = senderNickname.takeIf { it.isNotEmpty() },
    senderFaceURL = senderFaceURL.takeIf { it.isNotEmpty() },
)

private fun ByteString.toContentString(): String? {
    if (size == 0) return null
    return utf8()
}
