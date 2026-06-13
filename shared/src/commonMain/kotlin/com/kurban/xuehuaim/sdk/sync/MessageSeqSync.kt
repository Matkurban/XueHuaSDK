package com.kurban.xuehuaim.sdk.sync

import com.kurban.xuehuaim.sdk.network.http.ConversationSeqInfo

internal object MessageSeqSync {
    const val SDK_INSTALLED_KV_KEY = "sdk_installed"
    const val CONNECT_PULL_NUMS = 1L
    const val DEFAULT_PULL_NUMS = 10L
    const val SPLIT_PULL_MSG_NUM = 100
    const val PULL_MSG_NUM_FOR_READ_DIFFUSION = 50

    fun isNotificationConversation(conversationId: String): Boolean =
        conversationId.startsWith("n_")

    fun buildNeedSyncSeqMap(
        syncedMaxSeqs: Map<String, Long>,
        serverMaxSeqs: Map<String, Long>,
        reinstalled: Boolean,
    ): Map<String, LongRange> {
        val needSync = linkedMapOf<String, LongRange>()
        if (reinstalled) {
            val messageSeqs = serverMaxSeqs.filterKeys { !isNotificationConversation(it) }
            for ((conversationId, maxSeq) in messageSeqs) {
                val synced = syncedMaxSeqs[conversationId] ?: 0L
                if (maxSeq > synced) {
                    needSync[conversationId] = (synced + 1)..maxSeq
                } else if (synced == 0L && maxSeq > 0) {
                    needSync[conversationId] = 0L..maxSeq
                }
            }
            return needSync
        }
        for ((conversationId, maxSeq) in serverMaxSeqs) {
            if (maxSeq == 0L) continue
            val synced = syncedMaxSeqs[conversationId]
            if (synced != null) {
                if (maxSeq > synced) needSync[conversationId] = (synced + 1)..maxSeq
            } else {
                needSync[conversationId] = 0L..maxSeq
            }
        }
        return needSync
    }

    fun notificationSeqsFromServer(
        serverMaxSeqs: Map<String, Long>,
    ): Map<String, Long> = serverMaxSeqs
        .filter { (id, seq) -> isNotificationConversation(id) && seq > 0 }
        .mapValues { it.value }

    fun serverMaxSeqMap(seqs: Map<String, ConversationSeqInfo>): Map<String, Long> =
        seqs.mapValues { it.value.maxSeq }

    fun getLostSeqListWithLimitLength(
        minSeq: Long,
        maxSeq: Long,
        haveSeqList: Set<Long>,
        limit: Int = PULL_MSG_NUM_FOR_READ_DIFFUSION,
    ): List<Long> {
        if (minSeq <= 0 || maxSeq <= 0 || minSeq > maxSeq) return emptyList()
        val lost = mutableListOf<Long>()
        for (seq in minSeq..maxSeq) {
            if (seq !in haveSeqList) {
                lost += seq
                if (lost.size >= limit) break
            }
        }
        return lost
    }

    fun unreadCountFromSeq(maxSeq: Long, hasReadSeq: Long): Int =
        (maxSeq - hasReadSeq).coerceAtLeast(0).toInt()
}
