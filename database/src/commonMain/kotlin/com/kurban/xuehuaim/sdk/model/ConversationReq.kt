package com.kurban.xuehuaim.sdk.model

import kotlinx.serialization.Serializable

@Serializable
data class ConversationReq(
    val userID: String? = null,
    val groupID: String? = null,
    val recvMsgOpt: Int? = null,
    val isPinned: Boolean? = null,
    val isPrivateChat: Boolean? = null,
    val ex: String? = null,
    val burnDuration: Int? = null,
    val isMsgDestruct: Boolean? = null,
    val msgDestructTime: Long? = null,
    val groupAtType: Int? = null,
)
