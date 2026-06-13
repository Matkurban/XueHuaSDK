package com.kurban.xuehuaim.sdk.util

import com.kurban.xuehuaim.sdk.model.ConversationInfo

object ConversationSort {

    fun simpleSort(list: List<ConversationInfo>): List<ConversationInfo> =
        list.sortedWith { a, b ->
            val aPinned = a.isPinned == true
            val bPinned = b.isPinned == true
            when {
                aPinned && !bPinned -> -1
                !aPinned && bPinned -> 1
                else -> {
                    val aCompare = maxOf(a.draftTextTime ?: 0L, a.latestMsgSendTime ?: 0L)
                    val bCompare = maxOf(b.draftTextTime ?: 0L, b.latestMsgSendTime ?: 0L)
                    bCompare.compareTo(aCompare)
                }
            }
        }
}
