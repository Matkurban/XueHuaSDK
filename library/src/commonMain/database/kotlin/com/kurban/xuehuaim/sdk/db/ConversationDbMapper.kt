package com.kurban.xuehuaim.sdk.db

import com.kurban.xuehuaim.sdk.enum.ConversationType
import com.kurban.xuehuaim.sdk.enum.GroupAtType
import com.kurban.xuehuaim.sdk.enum.ReceiveMessageOpt
import com.kurban.xuehuaim.sdk.model.ConversationInfo
import com.kurban.xuehuaim.sdk.model.Message
import com.kurban.xuehuaim.sdk.util.withParsedContent
import kotlinx.serialization.json.Json

internal object ConversationDbMapper {
    private val json = Json { ignoreUnknownKeys = true }

    fun fromRow(row: Local_conversations): ConversationInfo = ConversationInfo(
        conversationID = row.conversationID,
        conversationType = row.conversationType?.toInt()?.let { v ->
            ConversationType.entries.find { it.value == v }
        },
        userID = row.userID,
        groupID = row.groupID,
        showName = row.showName,
        faceURL = row.faceURL,
        recvMsgOpt = row.recvMsgOpt?.toInt()?.let { v ->
            ReceiveMessageOpt.entries.find { it.value == v }
        },
        unreadCount = row.unreadCount.toInt(),
        latestMsg = row.latestMsg?.let { json.decodeFromString<Message>(it).withParsedContent() },
        latestMsgSendTime = row.latestMsgSendTime,
        draftText = row.draftText,
        draftTextTime = row.draftTextTime,
        isPinned = row.isPinned == 1L,
        isPrivateChat = row.isPrivateChat == 1L,
        burnDuration = row.burnDuration?.toInt(),
        isMsgDestruct = row.isMsgDestruct == 1L,
        msgDestructTime = row.msgDestructTime,
        ex = row.ex,
        isNotInGroup = row.isNotInGroup == 1L,
        groupAtType = row.groupAtType?.toInt()?.let { v ->
            GroupAtType.entries.find { it.value == v }
        },
        maxSeq = row.maxSeq,
        minSeq = row.minSeq,
        hasReadSeq = row.hasReadSeq,
    )
}
