package com.kurban.xuehuaim.sdk.sync

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.model.Message

internal class MessageGapChecker(
    private val databaseService: DatabaseService,
    private val gapPuller: suspend (conversationId: String, lostSeqs: List<Long>, isReverse: Boolean) -> Unit,
) {
    private val messagePullForwardEndSeqMap = mutableMapOf<String, Long>()

    suspend fun fetchMessagesWithGapCheck(
        conversationId: String,
        count: Int,
        startClientMsgId: String?,
        isReverse: Boolean = false,
    ): MessageGapResult {
        val queryCount = count.coerceAtLeast(1)
        var list = loadLocalMessages(conversationId, queryCount, startClientMsgId, isReverse)
        var isEnd = false
        var lastMinSeq: Long? = null

        if (!isReverse) {
            val maxSeq = validateInternalGaps(conversationId, list, isReverse)
            validateInterBlockGaps(conversationId, maxSeq, isReverse, list)
            val endResult = validateEndBlockContinuity(conversationId, queryCount, list, isReverse)
            isEnd = endResult.isEnd
            lastMinSeq = endResult.lastMinSeq
            if (endResult.shouldRefetch) {
                list = loadLocalMessages(conversationId, queryCount, startClientMsgId, isReverse)
            }
        } else {
            val minSeq = validateInternalGaps(conversationId, list, isReverse)
            validateInterBlockGaps(conversationId, minSeq, isReverse, list)
            val endResult = validateEndBlockContinuity(conversationId, queryCount, list, isReverse)
            isEnd = endResult.isEnd
            lastMinSeq = endResult.lastMinSeq
            if (endResult.shouldRefetch) {
                list = loadLocalMessages(conversationId, queryCount, startClientMsgId, isReverse)
            }
        }

        val result = if (list.size <= queryCount) list else list.take(queryCount)
        if (!isReverse && result.isNotEmpty()) {
            messagePullForwardEndSeqMap[conversationId] = result.last().seq
        }
        return MessageGapResult(
            messages = result,
            isEnd = isEnd,
            lastMinSeq = lastMinSeq,
        )
    }

    private suspend fun loadLocalMessages(
        conversationId: String,
        count: Int,
        startClientMsgId: String?,
        isReverse: Boolean,
    ): List<Message> {
        if (!startClientMsgId.isNullOrBlank()) {
            val anchor = databaseService.getMessageByClientMsgId(startClientMsgId) ?: return emptyList()
            val beforeSeq = anchor.seq
            if (beforeSeq <= 0) return emptyList()
            val loaded = databaseService.getMessagesBySeqDesc(conversationId, count, beforeSeq)
            return if (isReverse) loaded.reversed() else loaded
        }
        val loaded = databaseService.getMessagesBySeqDesc(conversationId, count + 1)
        return if (isReverse) loaded.reversed() else loaded
    }

    private suspend fun validateInternalGaps(
        conversationId: String,
        list: List<Message>,
        isReverse: Boolean,
    ): Long {
        val seqs = list.map { it.seq }.filter { it > 0 }
        if (seqs.isEmpty()) return 0L
        val maxSeq = seqs.max()
        val minSeq = seqs.min()
        val haveSeqList = seqs.toSet()
        val lost = MessageSeqSync.getLostSeqListWithLimitLength(minSeq, maxSeq, haveSeqList)
        if (lost.isNotEmpty()) {
            gapPuller(conversationId, lost, isReverse)
        }
        return if (isReverse) minSeq else maxSeq
    }

    private suspend fun validateInterBlockGaps(
        conversationId: String,
        thisStartSeq: Long,
        isReverse: Boolean,
        list: List<Message>,
    ) {
        if (thisStartSeq <= 0) return
        val lastEndSeq = messagePullForwardEndSeqMap[conversationId] ?: return
        val isLostSeq = if (isReverse) {
            lastEndSeq + 1 != thisStartSeq
        } else {
            thisStartSeq + 1 != lastEndSeq
        }
        if (!isLostSeq || lastEndSeq == 0L) return
        val startSeq = if (isReverse) lastEndSeq + 1 else thisStartSeq + 1
        val endSeq = if (isReverse) thisStartSeq - 1 else lastEndSeq - 1
        if (endSeq < startSeq) return
        val lost = MessageSeqSync.getLostSeqListWithLimitLength(startSeq, endSeq, emptySet())
        if (lost.isNotEmpty()) {
            gapPuller(conversationId, lost, isReverse)
        }
    }

    private suspend fun validateEndBlockContinuity(
        conversationId: String,
        count: Int,
        list: List<Message>,
        isReverse: Boolean,
    ): EndBlockResult {
        if (list.size >= count) return EndBlockResult()
        val conv = databaseService.getConversation(conversationId) ?: return EndBlockResult(isEnd = true)
        val seqs = list.map { it.seq }.filter { it > 0 }
        if (isReverse) {
            val currentMaxSeq = conv.maxSeq
            val maxSeq = seqs.maxOrNull() ?: 0L
            if (maxSeq >= currentMaxSeq) return EndBlockResult(isEnd = true)
            val lost = MessageSeqSync.getLostSeqListWithLimitLength(maxSeq + 1, currentMaxSeq, emptySet())
            if (lost.isEmpty()) return EndBlockResult(isEnd = true)
            gapPuller(conversationId, lost, isReverse)
            return EndBlockResult(shouldRefetch = true, lastMinSeq = seqs.minOrNull())
        }
        val userCanPullMinSeq = conv.minSeq.coerceAtLeast(1L)
        val minSeq = seqs.minOrNull() ?: 0L
        val lastMinSeq = messagePullForwardEndSeqMap[conversationId]
        if (minSeq <= userCanPullMinSeq) return EndBlockResult(isEnd = true, lastMinSeq = minSeq)
        if (minSeq == 0L && (lastMinSeq ?: 0L) <= userCanPullMinSeq) {
            return EndBlockResult(isEnd = true, lastMinSeq = minSeq)
        }
        val lost = MessageSeqSync.getLostSeqListWithLimitLength(userCanPullMinSeq, minSeq - 1, emptySet())
        if (lost.isEmpty()) return EndBlockResult(isEnd = true, lastMinSeq = minSeq)
        gapPuller(conversationId, lost, isReverse)
        return EndBlockResult(shouldRefetch = true, lastMinSeq = minSeq)
    }

    internal data class MessageGapResult(
        val messages: List<Message>,
        val isEnd: Boolean = false,
        val lastMinSeq: Long? = null,
    )

    private data class EndBlockResult(
        val isEnd: Boolean = false,
        val lastMinSeq: Long? = null,
        val shouldRefetch: Boolean = false,
    )
}
