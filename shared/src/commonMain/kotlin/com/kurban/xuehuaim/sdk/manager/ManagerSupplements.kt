package com.kurban.xuehuaim.sdk.manager

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.enum.MessageType
import com.kurban.xuehuaim.sdk.event.ConversationEvent
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.model.Message
import com.kurban.xuehuaim.sdk.util.withParsedContent

internal object ConversationLatestMsgHelper {
    suspend fun updateConversationIfLatestMsg(
        databaseService: DatabaseService,
        eventEmitter: SdkEventEmitter,
        conversationId: String,
        clientMsgId: String,
    ) {
        val conv = databaseService.getConversation(conversationId) ?: return
        if (conv.latestMsg?.clientMsgID != clientMsgId) return
        val next = databaseService.getMessages(conversationId, 1).firstOrNull()
        val updated = if (next != null) {
            conv.copy(
                latestMsg = next.withParsedContent(),
                latestMsgSendTime = next.sendTime ?: next.createTime,
            )
        } else {
            conv.copy(latestMsg = null, latestMsgSendTime = 0)
        }
        databaseService.insertOrReplaceConversation(updated)
        if ((updated.latestMsgSendTime ?: 0) > 0) {
            eventEmitter.emitConversation(ConversationEvent.Changed(updated))
        }
    }

    suspend fun updateLatestMsgFromMessage(
        databaseService: DatabaseService,
        eventEmitter: SdkEventEmitter,
        conversationId: String,
        message: Message,
    ) {
        val conv = databaseService.getConversation(conversationId) ?: return
        if (conv.latestMsg?.clientMsgID != message.clientMsgID) return
        val updated = conv.copy(latestMsg = message.withParsedContent())
        databaseService.insertOrReplaceConversation(updated)
        eventEmitter.emitConversation(ConversationEvent.Changed(updated))
    }
}

internal fun Message.isRevokeNotification(): Boolean =
    contentType == MessageType.MSG_REVOKE
