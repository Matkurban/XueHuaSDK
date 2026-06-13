package com.kurban.xuehuaim.sdk.network.ws

import com.kurban.xuehuaim.sdk.enum.ConnectionState
import com.kurban.xuehuaim.sdk.event.ConnectionEvent
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.platform.GzipCodec
import com.kurban.xuehuaim.sdk.platform.ioDispatcher
import com.kurban.xuehuaim.sdk.platform.sdkScope
import com.kurban.xuehuaim.sdk.util.OperationIdGenerator
import com.kurban.xuehuaim.sdk.util.SdkLogger
import com.kurban.xuehuaim.sdk.util.System
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.seconds

internal class WebSocketService(
    private val wsUrl: String,
    private val platformId: Int,
    private val gzipCodec: GzipCodec,
    private val eventEmitter: SdkEventEmitter,
    private val httpClient: HttpClient,
) {
    private val log = SdkLogger.tag("WebSocketService")
    private val codec = WsCodec(gzipCodec, enableCompression = gzipCodec.isSupported)
    private val json = Json { ignoreUnknownKeys = true }
    private val pendingRequests = mutableMapOf<String, CompletableDeferred<WsResponse>>()
    private val pendingMutex = Mutex()
    private var session: DefaultClientWebSocketSession? = null
    private var connectionJob: Job? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var userID: String = ""
    private var token: String = ""
    private var userDisconnected = false
    private var isBackground = false
    private var reconnectAttempts = 0
    private var msgIncrCounter = 0
    private val backoffIntervals = listOf(1, 2, 4, 8, 16)

    var onPushMsg: (suspend (WsResponse) -> Unit)? = null
    var onConnected: (suspend () -> Unit)? = null
    var onUserOnlineStatusChanged: (suspend (WsResponse) -> Unit)? = null

    fun connect(userID: String, token: String) {
        this.userID = userID
        this.token = token
        userDisconnected = false
        reconnectAttempts = 0
        connectionJob?.cancel()
        connectionJob = sdkScope.launch(ioDispatcher) {
            doConnect()
        }
    }

    fun disconnect() {
        userDisconnected = true
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        connectionJob?.cancel()
        connectionJob = null
        session = null
        eventEmitter.setConnectionState(ConnectionState.DISCONNECTED)
    }

    suspend fun sendRequest(request: WsRequest, timeoutSeconds: Long = 10): WsResponse {
        val session = session ?: throw IllegalStateException("WebSocket not connected")
        val msgIncr = nextMsgIncr()
        request.msgIncr = msgIncr
        val deferred = CompletableDeferred<WsResponse>()
        pendingMutex.withLock {
            pendingRequests[msgIncr] = deferred
        }
        val encoded = codec.encodeRequest(
            request.copy(
                token = token,
                sendID = userID,
                operationID = OperationIdGenerator.generate(),
                msgIncr = msgIncr,
            ),
        )
        session.send(Frame.Binary(true, encoded))
        return withTimeoutOrNull(timeoutSeconds.seconds) { deferred.await() }
            ?: throw IllegalStateException("WebSocket request timeout: ${request.reqIdentifier}")
    }

    private suspend fun doConnect() {
        eventEmitter.setConnectionState(ConnectionState.CONNECTING)
        eventEmitter.emitConnection(ConnectionEvent.Connecting)
        try {
            httpClient.webSocket(urlString = buildWsUrl()) {
                session = this
                reconnectAttempts = 0
                eventEmitter.setConnectionState(ConnectionState.CONNECTED)
                eventEmitter.emitConnection(ConnectionEvent.ConnectSuccess)
                startHeartbeat()
                onConnected?.invoke()
                listenIncoming(this@webSocket)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error(e) { "WebSocket connect failed" }
            eventEmitter.setConnectionState(ConnectionState.DISCONNECTED)
            eventEmitter.emitConnection(
                ConnectionEvent.ConnectFailed(
                    -1,
                    e.message ?: "connect failed"
                )
            )
            scheduleReconnect()
        } finally {
            heartbeatJob?.cancel()
            session = null
            if (!userDisconnected) {
                eventEmitter.setConnectionState(ConnectionState.DISCONNECTED)
            }
        }
    }

    private suspend fun listenIncoming(session: DefaultClientWebSocketSession) {
        try {
            for (frame in session.incoming) {
                when (frame) {
                    is Frame.Binary -> handleBinaryFrame(frame.data)
                    is Frame.Text -> handleTextFrame(frame.readText())
                    else -> Unit
                }
            }
        } finally {
            if (!userDisconnected) {
                eventEmitter.setConnectionState(ConnectionState.RECONNECTING)
                scheduleReconnect()
            }
        }
    }

    private suspend fun handleTextFrame(text: String) {
        try {
            val root = json.parseToJsonElement(text).jsonObject
            when (val type = root["type"]?.jsonPrimitive?.content) {
                "pong" -> return
                "ping" -> return
                null, "" -> {
                    val errCode = root["errCode"]?.jsonPrimitive?.intOrNull
                        ?: root["code"]?.jsonPrimitive?.intOrNull
                    if (errCode != null && errCode != 0) {
                        handleAuthHandshakeError(
                            errCode,
                            root["errMsg"]?.jsonPrimitive?.content ?: text
                        )
                    }
                }

                else -> log.warn { "unknown text message type: $type" }
            }
        } catch (e: Exception) {
            if (text == "pong") return
            log.warn(e) { "failed to parse text frame: $text" }
        }
    }

    private suspend fun handleAuthHandshakeError(errCode: Int, errMsg: String) {
        when (errCode) {
            1501 -> eventEmitter.emitConnection(ConnectionEvent.TokenExpired)
            1503 -> eventEmitter.emitConnection(ConnectionEvent.TokenInvalid)
            1506 -> {
                eventEmitter.emitConnection(ConnectionEvent.KickedOffline(errMsg))
                disconnect()
            }

            1502, 1503, 1504, 1505, 1507 -> {
                eventEmitter.emitConnection(ConnectionEvent.ConnectFailed(errCode, errMsg))
                userDisconnected = true
                disconnect()
            }
        }
    }

    private suspend fun handleBinaryFrame(data: ByteArray) {
        val response = try {
            codec.decodeResponse(data)
        } catch (e: Exception) {
            log.warn(e) { "failed to decode websocket binary frame" }
            return
        }
        when (response.reqIdentifier) {
            WsIdentifier.PUSH_MSG -> onPushMsg?.invoke(response)
            WsIdentifier.KICK_ONLINE_MSG -> {
                eventEmitter.emitConnection(ConnectionEvent.KickedOffline(response.errMsg))
                disconnect()
            }

            WsIdentifier.LOGOUT_MSG -> {
                eventEmitter.emitConnection(ConnectionEvent.LogoutForced)
                disconnect()
            }

            WsIdentifier.WS_SUB_USER_ONLINE_STATUS -> onUserOnlineStatusChanged?.invoke(response)
            else -> completePending(response)
        }
    }

    private suspend fun completePending(response: WsResponse) {
        pendingMutex.withLock {
            pendingRequests.remove(response.msgIncr)?.complete(response)
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = sdkScope.launch(ioDispatcher) {
            while (isActive) {
                delay(24.seconds)
                try {
                    val opId = OperationIdGenerator.generate()
                    session?.send(Frame.Text("""{"type":"ping","body":"$opId"}"""))
                } catch (_: Exception) {
                    break
                }
            }
        }
    }

    private fun scheduleReconnect() {
        if (userDisconnected || reconnectAttempts >= 300) return
        reconnectJob?.cancel()
        reconnectJob = sdkScope.launch(ioDispatcher) {
            val index = reconnectAttempts.coerceAtMost(backoffIntervals.lastIndex)
            delay(backoffIntervals[index].seconds)
            reconnectAttempts++
            doConnect()
        }
    }

    private fun buildWsUrl(): String = buildString {
        val separator = if (wsUrl.contains("?")) "&" else "?"
        append(wsUrl)
        append(separator)
        append("sendID=").append(userID)
        append("&token=").append(token)
        append("&platformID=").append(platformId)
        append("&operationID=").append(OperationIdGenerator.generate())
        append("&isBackground=").append(isBackground)
        append("&sdkType=").append(WsSdkType.JS)
        append("&isMsgResp=true")
        if (gzipCodec.isSupported) {
            append("&compression=").append(WsCompression.GZIP)
        }
    }

    private fun nextMsgIncr(): String {
        msgIncrCounter++
        return "${userID}_${System.currentTimeMillis()}_$msgIncrCounter"
    }
}
