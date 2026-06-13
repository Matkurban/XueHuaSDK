package com.kurban.xuehuaim.sdk.db

import com.kurban.xuehuaim.sdk.enum.ConversationType
import com.kurban.xuehuaim.sdk.enum.MessageStatus
import com.kurban.xuehuaim.sdk.enum.MessageType
import com.kurban.xuehuaim.sdk.model.Message

internal object MessageDbMapper {
    fun fromRow(row: Local_chat_logs): Message = Message(
        clientMsgID = row.clientMsgID,
        serverMsgID = row.serverMsgID,
        sendID = row.sendID,
        recvID = row.recvID,
        platformID = row.senderPlatformID?.toInt(),
        senderNickname = row.senderNickname,
        senderFaceUrl = row.senderFaceUrl,
        groupID = row.groupID,
        sessionType = row.sessionType?.toInt()?.let { v ->
            ConversationType.entries.find { it.value == v }
        },
        msgFrom = row.msgFrom?.toInt(),
        contentType = row.contentType?.toInt()?.let { v ->
            MessageType.entries.find { it.value == v }
        },
        content = row.content,
        isRead = row.isRead == 1L,
        status = row.status?.toInt()?.let { v ->
            MessageStatus.entries.find { it.value == v }
        },
        seq = row.seq,
        sendTime = row.sendTime,
        createTime = row.createTime,
        attachedInfo = row.attachedInfo,
        ex = row.ex,
        localEx = row.localEx,
        conversationID = row.conversationID,
    )
}
