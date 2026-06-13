package com.kurban.xuehuaim.sdk.manager

import com.kurban.xuehuaim.sdk.enum.CallState
import com.kurban.xuehuaim.sdk.enum.CallType
import com.kurban.xuehuaim.sdk.event.CallEvent
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.model.CallSession
import com.kurban.xuehuaim.sdk.model.CallSignaling
import com.kurban.xuehuaim.sdk.network.http.ImApiService
import com.kurban.xuehuaim.sdk.platform.ioDispatcher
import com.kurban.xuehuaim.sdk.util.System
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class CallManager internal constructor(
    private val apiService: ImApiService,
    private val messageManager: MessageManager,
    private val eventEmitter: SdkEventEmitter,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private var currentUserId: String = ""
    private var currentSession: CallSession? = null
    private var inviteTimerJob: Job? = null
    private var incomingTimerJob: Job? = null

    val session: CallSession? get() = currentSession

    val isBusy: Boolean get() = currentSession != null

    fun setCurrentUserId(userId: String) {
        currentUserId = userId
    }

    suspend fun invite(
        inviteeUserIDs: List<String>,
        callType: CallType,
        timeout: Int = 60,
    ): CallSession = withContext(ioDispatcher) {
        if (currentSession != null) error("已在通话中，请先结束当前通话")
        require(inviteeUserIDs.isNotEmpty()) { "inviteeUserIDs 不能为空" }
        require(inviteeUserIDs.size <= 8) { "最多邀请 8 人" }

        val result = apiService.createMeeting(
            creatorUserID = currentUserId,
            callType = callType.apiValue(),
            inviteeUserIDs = inviteeUserIDs,
        )

        val session = CallSession(
            roomID = result.roomID,
            callType = callType,
            inviterUserID = currentUserId,
            inviteeUserIDs = inviteeUserIDs,
            liveURL = result.liveURL,
            token = result.token,
            state = CallState.CALLING,
            createTime = System.currentTimeMillis(),
        )
        currentSession = session

        val signaling = CallSignaling(
            action = SignalAction.INVITE,
            roomID = result.roomID,
            callType = callType.apiValue(),
            inviterUserID = currentUserId,
            inviteeUserIDs = inviteeUserIDs,
            liveURL = result.liveURL,
            timeout = timeout,
            timestamp = System.currentTimeMillis(),
        )
        inviteeUserIDs.forEach { userId ->
            sendSignaling(signaling, userId, isOnlineOnly = true)
        }

        startInviteTimer(timeout, session)
        result.busyUsers.forEach { busyUserId ->
            eventEmitter.emitCall(CallEvent.CallBusy(session, busyUserId))
        }
        session
    }

    suspend fun accept(roomID: String): CallSession = withContext(ioDispatcher) {
        val session = currentSession ?: error("没有对应的来电会话")
        require(session.roomID == roomID) { "没有对应的来电会话" }
        require(session.state == CallState.RINGING) { "当前状态不允许接受通话: ${session.state}" }

        cancelIncomingTimer()
        val result = apiService.joinMeeting(roomID = roomID, userID = currentUserId)
        val updated = session.copy(
            token = result.token,
            liveURL = result.liveURL.ifBlank { session.liveURL },
            state = CallState.CONNECTED,
            connectTime = System.currentTimeMillis(),
        )
        currentSession = updated

        val signaling = CallSignaling(
            action = SignalAction.ACCEPT,
            roomID = roomID,
            callType = session.callType.apiValue(),
            inviterUserID = session.inviterUserID,
            inviteeUserIDs = session.inviteeUserIDs,
            timestamp = System.currentTimeMillis(),
        )
        sendSignaling(signaling, session.inviterUserID, isOnlineOnly = true)
        eventEmitter.emitCall(CallEvent.CallAccepted(updated))
        updated
    }

    suspend fun reject(roomID: String) = withContext(ioDispatcher) {
        val session = currentSession ?: error("没有对应的来电会话")
        require(session.roomID == roomID) { "没有对应的来电会话" }

        cancelIncomingTimer()
        val signaling = CallSignaling(
            action = SignalAction.REJECT,
            roomID = roomID,
            callType = session.callType.apiValue(),
            inviterUserID = session.inviterUserID,
            inviteeUserIDs = session.inviteeUserIDs,
            timestamp = System.currentTimeMillis(),
        )
        sendSignaling(signaling, session.inviterUserID, isOnlineOnly = true)
        eventEmitter.emitCall(CallEvent.CallRejected(roomID))
        cleanupSession()
    }

    suspend fun cancel() = withContext(ioDispatcher) {
        val session = currentSession ?: return@withContext
        if (session.state != CallState.CALLING) error("当前状态不允许取消: ${session.state}")

        cancelInviteTimer()
        val signaling = CallSignaling(
            action = SignalAction.CANCEL,
            roomID = session.roomID,
            callType = session.callType.apiValue(),
            inviterUserID = session.inviterUserID,
            inviteeUserIDs = session.inviteeUserIDs,
            timestamp = System.currentTimeMillis(),
        )
        session.inviteeUserIDs.forEach { userId ->
            sendSignaling(signaling, userId, isOnlineOnly = true)
        }
        apiService.endMeeting(roomID = session.roomID, userID = currentUserId)
        eventEmitter.emitCall(CallEvent.CallEnded(session.roomID, "cancelled"))
        cleanupSession()
    }

    suspend fun hangup() = withContext(ioDispatcher) {
        val session = currentSession ?: return@withContext

        cancelInviteTimer()
        cancelIncomingTimer()

        val signaling = CallSignaling(
            action = SignalAction.HANGUP,
            roomID = session.roomID,
            callType = session.callType.apiValue(),
            inviterUserID = session.inviterUserID,
            inviteeUserIDs = session.inviteeUserIDs,
            timestamp = System.currentTimeMillis(),
        )
        val notifyUserIDs = buildSet {
            if (session.inviterUserID == currentUserId) {
                addAll(session.inviteeUserIDs)
            } else {
                add(session.inviterUserID)
                if (session.isGroupCall) {
                    addAll(session.inviteeUserIDs)
                    remove(currentUserId)
                }
            }
        }
        notifyUserIDs.forEach { userId ->
            sendSignaling(signaling, userId, isOnlineOnly = true)
        }

        when {
            session.state == CallState.CONNECTED && session.inviterUserID == currentUserId ->
                apiService.endMeeting(roomID = session.roomID, userID = currentUserId)

            else ->
                apiService.leaveMeeting(roomID = session.roomID, userID = currentUserId)
        }

        eventEmitter.emitCall(CallEvent.CallEnded(session.roomID, "hangup"))
        cleanupSession()
    }

    fun getSession(roomId: String): CallSession? =
        currentSession?.takeIf { it.roomID == roomId }

    fun handleSignalingMessage(senderUserID: String, data: String): Boolean {
        return try {
            val root = json.parseToJsonElement(data).jsonObject
            if (root["businessID"]?.jsonPrimitive?.content != CallSignaling.BUSINESS_ID) return false
            val signalingData = root["data"]?.jsonObject ?: return false
            val signaling = json.decodeFromJsonElement(CallSignaling.serializer(), signalingData)
            when (signaling.action) {
                SignalAction.INVITE -> handleInvite(senderUserID, signaling)
                SignalAction.ACCEPT -> handleAccept(senderUserID, signaling)
                SignalAction.REJECT -> handleReject(senderUserID, signaling)
                SignalAction.CANCEL -> handleCancel(senderUserID, signaling)
                SignalAction.HANGUP -> handleHangup(senderUserID, signaling)
                SignalAction.BUSY -> handleBusy(senderUserID, signaling)
                SignalAction.TIMEOUT -> handleTimeout(senderUserID, signaling)
                else -> Unit
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun handleInvite(senderUserID: String, signaling: CallSignaling) {
        val elapsed = System.currentTimeMillis() - signaling.timestamp
        if (elapsed > signaling.timeout * 1000L) return

        if (currentSession != null) {
            val busy = signaling.copy(
                action = SignalAction.BUSY,
                timestamp = System.currentTimeMillis(),
            )
            sendSignaling(busy, senderUserID, isOnlineOnly = true)
            return
        }

        val session = CallSession(
            roomID = signaling.roomID,
            callType = CallType.fromApiValue(signaling.callType),
            inviterUserID = signaling.inviterUserID,
            inviteeUserIDs = signaling.inviteeUserIDs,
            liveURL = signaling.liveURL,
            state = CallState.RINGING,
            createTime = System.currentTimeMillis(),
        )
        currentSession = session
        startIncomingTimer(signaling.timeout, session)
        scope.launch { eventEmitter.emitCall(CallEvent.IncomingCall(session)) }
    }

    private fun handleAccept(senderUserID: String, signaling: CallSignaling) {
        val session = currentSession ?: return
        if (session.roomID != signaling.roomID || session.state != CallState.CALLING) return
        cancelInviteTimer()
        val updated = session.copy(
            state = CallState.CONNECTED,
            connectTime = System.currentTimeMillis(),
        )
        currentSession = updated
        scope.launch { eventEmitter.emitCall(CallEvent.CallAccepted(updated)) }
    }

    private fun handleReject(senderUserID: String, signaling: CallSignaling) {
        val session = currentSession ?: return
        if (session.roomID != signaling.roomID) return
        cancelInviteTimer()
        scope.launch { eventEmitter.emitCall(CallEvent.CallRejected(session.roomID)) }
        cleanupSession()
    }

    private fun handleCancel(senderUserID: String, signaling: CallSignaling) {
        val session = currentSession ?: return
        if (session.roomID != signaling.roomID) return
        cancelIncomingTimer()
        scope.launch { eventEmitter.emitCall(CallEvent.CallEnded(session.roomID, "cancelled")) }
        cleanupSession()
    }

    private fun handleHangup(senderUserID: String, signaling: CallSignaling) {
        val session = currentSession ?: return
        if (session.roomID != signaling.roomID) return
        cancelInviteTimer()
        cancelIncomingTimer()
        scope.launch { eventEmitter.emitCall(CallEvent.CallEnded(session.roomID, "remote_hangup")) }
        cleanupSession()
    }

    private fun handleBusy(senderUserID: String, signaling: CallSignaling) {
        val session = currentSession ?: return
        if (session.roomID != signaling.roomID) return
        scope.launch { eventEmitter.emitCall(CallEvent.CallBusy(session, senderUserID)) }
    }

    private fun handleTimeout(senderUserID: String, signaling: CallSignaling) {
        val session = currentSession ?: return
        if (session.roomID != signaling.roomID) return
        cancelIncomingTimer()
        scope.launch { eventEmitter.emitCall(CallEvent.CallEnded(session.roomID, "timeout")) }
        cleanupSession()
    }

    private fun sendSignaling(signaling: CallSignaling, toUserID: String, isOnlineOnly: Boolean) {
        val payload = buildJsonObject {
            put("businessID", CallSignaling.BUSINESS_ID)
            put("data", json.encodeToJsonElement(CallSignaling.serializer(), signaling))
        }.toString()
        messageManager.launchSendRtcSignaling(toUserID, payload, isOnlineOnly)
    }

    private fun startInviteTimer(timeoutSeconds: Int, session: CallSession) {
        cancelInviteTimer()
        inviteTimerJob = messageManager.launchTimer(timeoutSeconds * 1000L) {
            val active = currentSession ?: return@launchTimer
            if (active.roomID != session.roomID || active.state != CallState.CALLING) return@launchTimer
            val signaling = CallSignaling(
                action = SignalAction.TIMEOUT,
                roomID = session.roomID,
                callType = session.callType.apiValue(),
                inviterUserID = session.inviterUserID,
                inviteeUserIDs = session.inviteeUserIDs,
                timestamp = System.currentTimeMillis(),
            )
            session.inviteeUserIDs.forEach { userId ->
                sendSignaling(signaling, userId, isOnlineOnly = true)
            }
            apiService.endMeeting(roomID = session.roomID, userID = currentUserId)
            eventEmitter.emitCall(CallEvent.CallEnded(session.roomID, "timeout"))
            cleanupSession()
        }
    }

    private fun startIncomingTimer(timeoutSeconds: Int, session: CallSession) {
        cancelIncomingTimer()
        incomingTimerJob = messageManager.launchTimer(timeoutSeconds * 1000L) {
            val active = currentSession ?: return@launchTimer
            if (active.roomID != session.roomID || active.state != CallState.RINGING) return@launchTimer
            eventEmitter.emitCall(CallEvent.CallEnded(session.roomID, "incoming_timeout"))
            cleanupSession()
        }
    }

    private fun cancelInviteTimer() {
        inviteTimerJob?.cancel()
        inviteTimerJob = null
    }

    private fun cancelIncomingTimer() {
        incomingTimerJob?.cancel()
        incomingTimerJob = null
    }

    private fun cleanupSession() {
        currentSession = null
        cancelInviteTimer()
        cancelIncomingTimer()
    }

    fun dispose() {
        cleanupSession()
    }
}

internal object SignalAction {
    const val INVITE = "invite"
    const val ACCEPT = "accept"
    const val REJECT = "reject"
    const val CANCEL = "cancel"
    const val HANGUP = "hangup"
    const val BUSY = "busy"
    const val TIMEOUT = "timeout"
}
