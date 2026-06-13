package com.kurban.xuehuaim.sdk.manager

import com.kurban.xuehuaim.sdk.enum.MessageStatus
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageSendResendPolicyTest {
    @Test
    fun allowsResendForFailedOrSendingOnly() {
        assertTrue(canResendMessage(MessageStatus.SEND_FAILED))
        assertTrue(canResendMessage(MessageStatus.SENDING))
        assertFalse(canResendMessage(MessageStatus.SEND_SUCCESS))
        assertFalse(canResendMessage(null))
    }

    private fun canResendMessage(status: MessageStatus?): Boolean = when (status) {
        MessageStatus.SEND_FAILED, MessageStatus.SENDING -> true
        else -> false
    }
}
