package com.kurban.xuehuaim.sdk.sync

import kotlin.test.Test
import kotlin.test.assertEquals

class MessageSeqSyncTest {

    @Test
    fun buildNeedSyncSeqMap_detectsGaps() {
        val synced = mapOf("c1" to 5L)
        val server = mapOf("c1" to 8L, "c2" to 3L)
        val needSync = MessageSeqSync.buildNeedSyncSeqMap(synced, server, reinstalled = false)
        assertEquals(6L..8L, needSync["c1"])
        assertEquals(0L..3L, needSync["c2"])
    }

    @Test
    fun unreadCountFromSeq_matchesGoSemantics() {
        assertEquals(3, MessageSeqSync.unreadCountFromSeq(maxSeq = 10, hasReadSeq = 7))
        assertEquals(0, MessageSeqSync.unreadCountFromSeq(maxSeq = 5, hasReadSeq = 8))
    }

    @Test
    fun getLostSeqListWithLimitLength_findsMissingSeqs() {
        val lost = MessageSeqSync.getLostSeqListWithLimitLength(
            minSeq = 2,
            maxSeq = 6,
            haveSeqList = setOf(2L, 4L, 6L),
            limit = 10,
        )
        assertEquals(listOf(3L, 5L), lost)
    }
}
