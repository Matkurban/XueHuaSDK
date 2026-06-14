package com.kurban.xuehuaim.sdk.flow

import com.kurban.xuehuaim.sdk.enum.ConnectionState
import com.kurban.xuehuaim.sdk.enum.LoginStatus
import com.kurban.xuehuaim.sdk.event.CallEvent
import com.kurban.xuehuaim.sdk.event.ConnectionEvent
import com.kurban.xuehuaim.sdk.event.ConversationEvent
import com.kurban.xuehuaim.sdk.event.ConversationSyncState
import com.kurban.xuehuaim.sdk.event.CustomBusinessEvent
import com.kurban.xuehuaim.sdk.event.FavoriteEvent
import com.kurban.xuehuaim.sdk.event.FriendshipEvent
import com.kurban.xuehuaim.sdk.event.GroupEvent
import com.kurban.xuehuaim.sdk.event.MessageEvent
import com.kurban.xuehuaim.sdk.event.MomentsEvent
import com.kurban.xuehuaim.sdk.event.RedPacketEvent
import com.kurban.xuehuaim.sdk.event.ServiceEvent
import com.kurban.xuehuaim.sdk.event.UploadProgressEvent
import com.kurban.xuehuaim.sdk.event.UserEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

internal class SdkEventEmitter {
    private val _loginStatus = MutableStateFlow(LoginStatus.LOGOUT)
    val loginStatus: StateFlow<LoginStatus> = _loginStatus.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectionEvents = createEventFlow<ConnectionEvent>()
    val connectionEvents: SharedFlow<ConnectionEvent> = _connectionEvents.asSharedFlow()

    private val _messageEvents = createEventFlow<MessageEvent>()
    val messageEvents: SharedFlow<MessageEvent> = _messageEvents.asSharedFlow()

    private val _conversationEvents = createEventFlow<ConversationEvent>()
    val conversationEvents: SharedFlow<ConversationEvent> = _conversationEvents.asSharedFlow()

    private val _conversationSyncState = MutableStateFlow<ConversationSyncState>(ConversationSyncState.Idle)
    val conversationSyncState: StateFlow<ConversationSyncState> = _conversationSyncState.asStateFlow()

    private val _friendshipEvents = createEventFlow<FriendshipEvent>()
    val friendshipEvents: SharedFlow<FriendshipEvent> = _friendshipEvents.asSharedFlow()

    private val _groupEvents = createEventFlow<GroupEvent>()
    val groupEvents: SharedFlow<GroupEvent> = _groupEvents.asSharedFlow()

    private val _userEvents = createEventFlow<UserEvent>()
    val userEvents: SharedFlow<UserEvent> = _userEvents.asSharedFlow()

    private val _callEvents = createEventFlow<CallEvent>()
    val callEvents: SharedFlow<CallEvent> = _callEvents.asSharedFlow()

    private val _redPacketEvents = createEventFlow<RedPacketEvent>()
    val redPacketEvents: SharedFlow<RedPacketEvent> = _redPacketEvents.asSharedFlow()

    private val _momentsEvents = createEventFlow<MomentsEvent>()
    val momentsEvents: SharedFlow<MomentsEvent> = _momentsEvents.asSharedFlow()

    private val _favoriteEvents = createEventFlow<FavoriteEvent>()
    val favoriteEvents: SharedFlow<FavoriteEvent> = _favoriteEvents.asSharedFlow()

    private val _uploadProgress = createEventFlow<UploadProgressEvent>()
    val uploadProgress: SharedFlow<UploadProgressEvent> = _uploadProgress.asSharedFlow()

    private val _customBusinessEvents = createEventFlow<CustomBusinessEvent>()
    val customBusinessEvents: SharedFlow<CustomBusinessEvent> = _customBusinessEvents.asSharedFlow()

    private val _serviceEvents = createEventFlow<ServiceEvent>()
    val serviceEvents: SharedFlow<ServiceEvent> = _serviceEvents.asSharedFlow()

    fun setLoginStatus(status: LoginStatus) {
        _loginStatus.value = status
    }

    fun setConnectionState(state: ConnectionState) {
        _connectionState.value = state
    }

    suspend fun emitConnection(event: ConnectionEvent) = _connectionEvents.emit(event)
    suspend fun emitMessage(event: MessageEvent) = _messageEvents.emit(event)
    suspend fun emitConversation(event: ConversationEvent) = _conversationEvents.emit(event)

    fun setConversationSyncState(state: ConversationSyncState) {
        _conversationSyncState.value = state
    }
    suspend fun emitFriendship(event: FriendshipEvent) = _friendshipEvents.emit(event)
    suspend fun emitGroup(event: GroupEvent) = _groupEvents.emit(event)
    suspend fun emitUser(event: UserEvent) = _userEvents.emit(event)
    suspend fun emitCall(event: CallEvent) = _callEvents.emit(event)
    suspend fun emitRedPacket(event: RedPacketEvent) = _redPacketEvents.emit(event)
    suspend fun emitMoments(event: MomentsEvent) = _momentsEvents.emit(event)
    suspend fun emitFavorite(event: FavoriteEvent) = _favoriteEvents.emit(event)
    suspend fun emitUploadProgress(event: UploadProgressEvent) = _uploadProgress.emit(event)

    suspend fun emitCustomBusiness(event: CustomBusinessEvent) = _customBusinessEvents.emit(event)

    suspend fun emitService(event: ServiceEvent) = _serviceEvents.emit(event)

    private companion object {
        fun <T> createEventFlow(): MutableSharedFlow<T> =
            MutableSharedFlow(extraBufferCapacity = 64)
    }
}
