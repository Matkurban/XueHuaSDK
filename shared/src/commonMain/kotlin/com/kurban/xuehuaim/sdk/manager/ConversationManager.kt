package com.kurban.xuehuaim.sdk.manager

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.enum.ConversationType
import com.kurban.xuehuaim.sdk.enum.GroupAtType
import com.kurban.xuehuaim.sdk.enum.MessageType
import com.kurban.xuehuaim.sdk.enum.ReceiveMessageOpt
import com.kurban.xuehuaim.sdk.enum.SdkErrorCode
import com.kurban.xuehuaim.sdk.exception.XueHuaException
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.model.ConversationInfo
import com.kurban.xuehuaim.sdk.model.ConversationReq
import com.kurban.xuehuaim.sdk.network.http.ImApiService
import com.kurban.xuehuaim.sdk.network.http.LoginUserIdProvider
import com.kurban.xuehuaim.sdk.network.ws.WebSocketService
import com.kurban.xuehuaim.sdk.network.ws.WsIdentifier
import com.kurban.xuehuaim.sdk.network.ws.WsRequest
import com.kurban.xuehuaim.sdk.platform.currentPlatform
import com.kurban.xuehuaim.sdk.platform.ioDispatcher
import com.kurban.xuehuaim.sdk.util.ClientMsgIdGenerator
import com.kurban.xuehuaim.sdk.util.OpenImUtils
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json


class ConversationManager internal constructor(
    private val apiService: ImApiService,
    private val databaseService: DatabaseService,
    private val webSocketService: WebSocketService,
    private val eventEmitter: SdkEventEmitter,
    private val loginUserId: LoginUserIdProvider,
) {
    private val markingAsRead = mutableSetOf<String>()

    fun getConversationIDBySessionType(sourceID: String, sessionType: Int): String =
        OpenImUtils.getConversationIDBySessionType(
            loginUserId().orEmpty(),
            sourceID,
            sessionType,
        )

    suspend fun getOneConversation(sourceID: String, sessionType: Int): ConversationInfo =
        withContext(ioDispatcher) {
            val conversationId = getConversationIDBySessionType(sourceID, sessionType)
            databaseService.getConversation(conversationId) ?: run {
                val created = ConversationInfo(
                    conversationID = conversationId,
                    conversationType = ConversationType.entries.find { it.value == sessionType },
                    userID = if (sessionType == ConversationType.SINGLE.value) sourceID else null,
                    groupID = if (sessionType != ConversationType.SINGLE.value) sourceID else null,
                    unreadCount = 0,
                )
                databaseService.insertOrReplaceConversation(created)
                databaseService.getConversation(conversationId) ?: created
            }
        }

    suspend fun getMultipleConversation(conversationIDList: List<String>): List<ConversationInfo> =
        withContext(ioDispatcher) {
            databaseService.getMultipleConversations(conversationIDList)
        }

    suspend fun searchConversations(name: String): List<ConversationInfo> =
        withContext(ioDispatcher) {
            databaseService.searchConversations(name)
        }

    suspend fun getAllConversationList(): List<ConversationInfo> = withContext(ioDispatcher) {
        com.kurban.xuehuaim.sdk.util.ConversationSort.simpleSort(databaseService.getVisibleConversations())
    }

    suspend fun syncConversationsFromServer(): Int = withContext(ioDispatcher) {
        com.kurban.xuehuaim.sdk.sync.ConversationSync.syncFromServer(
            apiService = apiService,
            databaseService = databaseService,
            eventEmitter = eventEmitter,
            userId = loginUserId.requireUserId(),
        )
    }

    suspend fun getConversationListSplit(
        offset: Int = 0,
        count: Int = 100
    ): List<ConversationInfo> =
        withContext(ioDispatcher) {
            com.kurban.xuehuaim.sdk.sync.VersionedListPager.fetchConversationsPage(
                databaseService = databaseService,
                offset = offset,
                count = count,
            )
        }

    suspend fun getConversation(conversationId: String): ConversationInfo? =
        withContext(ioDispatcher) {
            databaseService.getConversation(conversationId)
        }

    fun simpleSort(list: List<ConversationInfo>): List<ConversationInfo> =
        com.kurban.xuehuaim.sdk.util.ConversationSort.simpleSort(list)

    suspend fun pinConversation(conversationId: String, isPinned: Boolean) =
        withContext(ioDispatcher) {
            setConversation(conversationId, ConversationReq(isPinned = isPinned))
        }

    suspend fun setConversation(conversationId: String, req: ConversationReq) =
        withContext(ioDispatcher) {
            val target = databaseService.getConversation(conversationId) ?: return@withContext
            val updated = target.copy(
                isPinned = req.isPinned ?: target.isPinned,
                recvMsgOpt = req.recvMsgOpt?.let { v ->
                    ReceiveMessageOpt.entries.find { it.value == v }
                } ?: target.recvMsgOpt,
                isPrivateChat = req.isPrivateChat ?: target.isPrivateChat,
                ex = req.ex ?: target.ex,
                burnDuration = req.burnDuration ?: target.burnDuration,
                isMsgDestruct = req.isMsgDestruct ?: target.isMsgDestruct,
                msgDestructTime = req.msgDestructTime ?: target.msgDestructTime,
                groupAtType = req.groupAtType?.let { v ->
                    GroupAtType.entries.find { it.value == v }
                } ?: target.groupAtType,
            )
            databaseService.insertOrReplaceConversation(updated)
            notifyConversationChanged(conversationId)
            apiService.setConversations(loginUserId.requireUserId(), conversationId, req)
        }

    suspend fun setConversationDraft(conversationId: String, draftText: String) =
        withContext(ioDispatcher) {
            val target = databaseService.getConversation(conversationId) ?: return@withContext
            val updated = target.copy(
                draftText = draftText,
                draftTextTime = com.kurban.xuehuaim.sdk.util.System.currentTimeMillis(),
            )
            databaseService.insertOrReplaceConversation(updated)
            notifyConversationChanged(conversationId)
        }

    suspend fun hideConversation(conversationId: String) = withContext(ioDispatcher) {
        databaseService.resetConversation(conversationId)
        notifyConversationChanged(conversationId)
    }

    suspend fun deleteConversationAndDeleteAllMsg(conversationId: String) =
        withContext(ioDispatcher) {
            val conv = databaseService.getConversation(conversationId)
                ?: throw XueHuaException.from(
                    SdkErrorCode.PARAM_ERROR,
                    "conversation not found: $conversationId"
                )
            apiService.clearConversationMsg(loginUserId.requireUserId(), listOf(conversationId))
            databaseService.deleteChatLogsByConversation(conversationId)
            val hasReadSeq = if (conv.maxSeq > 0) conv.maxSeq else conv.hasReadSeq
            if (hasReadSeq > 0) {
                databaseService.updateConversationUnread(conversationId, 0, hasReadSeq)
            }
            databaseService.resetConversation(conversationId)
            notifyConversationChanged(conversationId)
            emitTotalUnread()
        }

    suspend fun markConversationMessageAsRead(conversationId: String) = withContext(ioDispatcher) {
        if (markingAsRead.contains(conversationId)) {
            emitTotalUnread()
            return@withContext
        }
        val conv = databaseService.getConversation(conversationId) ?: run {
            emitTotalUnread()
            return@withContext
        }
        if (conv.unreadCount == 0) return@withContext
        markingAsRead.add(conversationId)
        try {
            var hasReadSeq = conv.maxSeq
            if (hasReadSeq <= 0) {
                val messages = databaseService.getMessages(conversationId, 1)
                hasReadSeq = messages.firstOrNull()?.seq ?: conv.hasReadSeq
            }
            databaseService.updateConversationUnread(conversationId, 0, hasReadSeq)
            notifyConversationChanged(conversationId)
            apiService.markConversationAsRead(
                loginUserId.requireUserId(),
                conversationId,
                hasReadSeq,
            )
        } finally {
            markingAsRead.remove(conversationId)
        }
    }

    suspend fun markAllConversationMessageAsRead() = withContext(ioDispatcher) {
        val unread = databaseService.getAllConversations().filter { it.unreadCount > 0 }
        unread.forEach { conv ->
            runCatching { markConversationMessageAsRead(conv.conversationID) }
        }
        emitTotalUnread()
    }

    suspend fun markMessagesAsReadByMsgID(conversationId: String, clientMsgIDs: List<String>) =
        withContext(ioDispatcher) {
            if (clientMsgIDs.isEmpty()) return@withContext
            val msgs = databaseService.getMessagesByClientMsgIds(clientMsgIDs)
            val selfUserId = loginUserId.requireUserId()
            val seqs = mutableListOf<Long>()
            val asReadIds = mutableListOf<String>()
            msgs.forEach { msg ->
                if (msg.isRead != true && msg.sendID != selfUserId) {
                    val seq = msg.seq
                    if (seq > 0) {
                        seqs.add(seq)
                        msg.clientMsgID.let(asReadIds::add)
                    }
                }
            }
            if (seqs.isEmpty()) return@withContext
            runCatching {
                apiService.markMsgsAsRead(selfUserId, conversationId, seqs)
            }
            val decr = databaseService.markConversationMessageAsReadDB(conversationId, asReadIds)
            databaseService.decrConversationUnreadCount(conversationId, decr)
            notifyConversationChanged(conversationId)
        }

    suspend fun clearConversationAndDeleteAllMsg(conversationId: String) =
        withContext(ioDispatcher) {
            val conv = databaseService.getConversation(conversationId)
                ?: throw XueHuaException.from(
                    SdkErrorCode.PARAM_ERROR,
                    "conversation not found: $conversationId"
                )
            apiService.clearConversationMsg(loginUserId.requireUserId(), listOf(conversationId))
            databaseService.deleteChatLogsByConversation(conversationId)
            val hasReadSeq = if (conv.maxSeq > 0) conv.maxSeq else conv.hasReadSeq
            if (hasReadSeq > 0) {
                databaseService.updateConversationUnread(conversationId, 0, hasReadSeq)
            }
            databaseService.clearConversation(conversationId)
            notifyConversationChanged(conversationId)
            emitTotalUnread()
        }

    suspend fun hideAllConversations() = withContext(ioDispatcher) {
        databaseService.hideAllConversations()
        val allConvs = databaseService.getVisibleConversations()
        allConvs.forEach { conv ->
            eventEmitter.emitConversation(
                com.kurban.xuehuaim.sdk.event.ConversationEvent.Changed(
                    conv
                )
            )
        }
        emitTotalUnread()
    }

    suspend fun changeInputStates(conversationId: String, focus: Boolean) =
        withContext(ioDispatcher) {
            val conv = databaseService.getConversation(conversationId) ?: return@withContext
            val typingPayload = """{"msgTips":"${if (focus) "yes" else "no"}"}"""
            val msgData = buildMap {
                put("sendID", loginUserId.requireUserId())
                put("recvID", conv.userID.orEmpty())
                put("groupID", conv.groupID.orEmpty())
                put("clientMsgID", ClientMsgIdGenerator.generate())
                put("sessionType", conv.conversationType?.value ?: ConversationType.SINGLE.value)
                put("msgFrom", 100)
                put("contentType", MessageType.TYPING.value)
                put("content", typingPayload)
                put("senderPlatformID", currentPlatform().value)
                put("createTime", com.kurban.xuehuaim.sdk.util.System.currentTimeMillis())
                put("sendTime", 0)
                put(
                    "options",
                    mapOf(
                        "history" to false,
                        "persistent" to false,
                        "senderSync" to false,
                        "conversationUpdate" to false,
                        "senderConversationUpdate" to false,
                        "unreadCount" to false,
                        "offlinePush" to false,
                    ),
                )
            }
            webSocketService.sendRequest(
                WsRequest(
                    reqIdentifier = WsIdentifier.SEND_MSG,
                    data = Json.encodeToString(msgData).encodeToByteArray(),
                ),
            )
        }

    suspend fun getInputStates(conversationId: String, userID: String): List<Int> =
        withContext(ioDispatcher) {
            emptyList()
        }

    suspend fun getTotalUnreadMsgCount(): Int = withContext(ioDispatcher) {
        databaseService.getTotalUnreadCount()
    }

    private suspend fun notifyConversationChanged(conversationId: String) {
        val conv = databaseService.getConversation(conversationId)
        if (conv != null && (conv.latestMsgSendTime ?: 0) > 0) {
            eventEmitter.emitConversation(
                com.kurban.xuehuaim.sdk.event.ConversationEvent.Changed(
                    conv
                )
            )
        }
        emitTotalUnread()
    }

    private suspend fun emitTotalUnread() {
        val total = databaseService.getTotalUnreadCount()
        eventEmitter.emitConversation(
            com.kurban.xuehuaim.sdk.event.ConversationEvent.TotalUnreadChanged(total),
        )
    }
}
