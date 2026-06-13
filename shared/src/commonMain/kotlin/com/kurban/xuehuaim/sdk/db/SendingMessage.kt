package com.kurban.xuehuaim.sdk.db

data class SendingMessage(
    val clientMsgID: String,
    val conversationID: String,
    val ex: String? = null,
)
