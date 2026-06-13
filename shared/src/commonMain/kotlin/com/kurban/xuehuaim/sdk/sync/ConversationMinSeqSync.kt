package com.kurban.xuehuaim.sdk.sync

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.network.sync.MsgList

internal object ConversationMinSeqSync {
    suspend fun applyMinSeqFromPull(
        databaseService: DatabaseService,
        conversationId: String,
        msgList: MsgList,
        isReverse: Boolean,
    ) {
        if (!msgList.isEnd || msgList.endSeq <= 0) return
        val conversation = databaseService.getConversation(conversationId) ?: return
        val updated = if (isReverse) {
            if (conversation.maxSeq == 0L || msgList.endSeq < conversation.maxSeq) {
                conversation.copy(maxSeq = msgList.endSeq)
            } else {
                return
            }
        } else {
            if (conversation.minSeq == 0L || msgList.endSeq > conversation.minSeq) {
                conversation.copy(minSeq = msgList.endSeq)
            } else {
                return
            }
        }
        databaseService.insertOrReplaceConversation(updated)
    }
}
