package com.kurban.xuehuaim.sdk.util

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.enum.ConversationType
import com.kurban.xuehuaim.sdk.event.ConversationEvent
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.model.ConversationInfo
import com.kurban.xuehuaim.sdk.model.Message
import com.kurban.xuehuaim.sdk.sync.MessageSeqSync

internal object ConversationMessageUpdater {
    suspend fun updateFromMessage(
        databaseService: DatabaseService,
        eventEmitter: SdkEventEmitter,
        message: Message,
        selfUserId: String,
        isOutgoingSend: Boolean = false,
    ) {
        val conversationId = message.conversationID?.takeIf { it.isNotBlank() } ?: return
        val sendTime = message.sendTime ?: message.createTime ?: 0L
        if (sendTime <= 0) return

        val existing = databaseService.getConversation(conversationId)
        val existingSendTime = existing?.latestMsgSendTime ?: 0L
        val existingSeq = existing?.latestMsg?.seq ?: 0L
        if (message.seq < existingSeq && sendTime <= existingSendTime) return

        val isBootstrap = existingSendTime == 0L
        val isIncoming = !isOutgoingSend && message.sendID != null && message.sendID != selfUserId
        val updatedMaxSeq = maxOf(existing?.maxSeq ?: 0L, message.seq)
        val unreadCount = when {
            isOutgoingSend -> existing?.unreadCount ?: 0
            isBootstrap -> existing?.unreadCount ?: 0
            existing == null -> MessageSeqSync.unreadCountFromSeq(updatedMaxSeq, existing?.hasReadSeq ?: 0L)
            else -> MessageSeqSync.unreadCountFromSeq(updatedMaxSeq, existing.hasReadSeq)
        }
        val peerUserId = when {
            message.sessionType == ConversationType.SINGLE -> {
                when {
                    message.sendID == selfUserId -> message.recvID
                    message.recvID == selfUserId -> message.sendID
                    else -> message.sendID ?: message.recvID
                }
            }

            else -> null
        }
        val updated = (existing ?: ConversationInfo(
            conversationID = conversationId,
            conversationType = message.sessionType,
            userID = peerUserId,
            groupID = message.groupID,
        )).copy(
            latestMsg = message.withParsedContent(),
            latestMsgSendTime = sendTime,
            unreadCount = unreadCount,
            maxSeq = updatedMaxSeq,
        ).normalizedForStorage()

        databaseService.insertOrReplaceConversation(updated)
        if ((updated.latestMsgSendTime ?: 0) > 0) {
            eventEmitter.emitConversation(ConversationEvent.Changed(updated))
            val total = databaseService.getTotalUnreadCount()
            eventEmitter.emitConversation(ConversationEvent.TotalUnreadChanged(total))
        }
    }
}
