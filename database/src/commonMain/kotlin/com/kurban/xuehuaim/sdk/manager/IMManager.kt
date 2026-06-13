package com.kurban.xuehuaim.sdk.manager

import com.kurban.xuehuaim.sdk.config.InitConfig
import com.kurban.xuehuaim.sdk.config.LogLevel
import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.enum.ConnectionState
import com.kurban.xuehuaim.sdk.enum.LoginStatus
import com.kurban.xuehuaim.sdk.enum.SdkErrorCode
import com.kurban.xuehuaim.sdk.enum.TokenCheckResult
import com.kurban.xuehuaim.sdk.event.CallEvent
import com.kurban.xuehuaim.sdk.event.ConnectionEvent
import com.kurban.xuehuaim.sdk.event.ConversationEvent
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
import com.kurban.xuehuaim.sdk.exception.XueHuaException
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.model.AuthCacheData
import com.kurban.xuehuaim.sdk.model.UploadFileResult
import com.kurban.xuehuaim.sdk.model.UserInfo
import com.kurban.xuehuaim.sdk.network.http.ImApiService
import com.kurban.xuehuaim.sdk.network.http.LoginReq
import com.kurban.xuehuaim.sdk.network.http.LoginUserIdHolder
import com.kurban.xuehuaim.sdk.network.http.SdkHttpClient
import com.kurban.xuehuaim.sdk.network.notify.NotificationDispatcher
import com.kurban.xuehuaim.sdk.network.sync.MsgSyncer
import com.kurban.xuehuaim.sdk.network.ws.WebSocketService
import com.kurban.xuehuaim.sdk.network.ws.createWsHttpClient
import com.kurban.xuehuaim.sdk.platform.DatabaseDriverFactory
import com.kurban.xuehuaim.sdk.platform.FileSystem
import com.kurban.xuehuaim.sdk.platform.GzipCodec
import com.kurban.xuehuaim.sdk.platform.currentPlatform
import com.kurban.xuehuaim.sdk.platform.defaultDbPath
import com.kurban.xuehuaim.sdk.platform.ioDispatcher
import com.kurban.xuehuaim.sdk.util.SdkLogger
import com.kurban.xuehuaim.sdk.util.configureSdkLogging
import com.kurban.xuehuaim.sdk.util.md5Hex
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class IMManager internal constructor(
    private val eventEmitter: SdkEventEmitter,
    private val httpClient: SdkHttpClient,
    private val apiService: ImApiService,
    private val databaseService: DatabaseService,
    private val webSocketService: WebSocketService,
    private val msgSyncer: MsgSyncer,
    private val notificationDispatcher: NotificationDispatcher,
    private val fileSystem: FileSystem,
    private val loginUserIdHolder: LoginUserIdHolder,
    private val fileUploadService: FileUploadService,
) {
    val loginStatus: StateFlow<LoginStatus> = eventEmitter.loginStatus

    val connectionState: StateFlow<ConnectionState> = eventEmitter.connectionState

    val connectionEvents: SharedFlow<ConnectionEvent> = eventEmitter.connectionEvents

    val messageEvents: SharedFlow<MessageEvent> = eventEmitter.messageEvents

    val conversationEvents: SharedFlow<ConversationEvent> = eventEmitter.conversationEvents

    val friendshipEvents: SharedFlow<FriendshipEvent> = eventEmitter.friendshipEvents

    val groupEvents: SharedFlow<GroupEvent> = eventEmitter.groupEvents

    val userEvents: SharedFlow<UserEvent> = eventEmitter.userEvents

    val callEvents: SharedFlow<CallEvent> = eventEmitter.callEvents

    val redPacketEvents: SharedFlow<RedPacketEvent> = eventEmitter.redPacketEvents

    val momentsEvents: SharedFlow<MomentsEvent> = eventEmitter.momentsEvents

    val favoriteEvents: SharedFlow<FavoriteEvent> = eventEmitter.favoriteEvents

    val uploadProgress: SharedFlow<UploadProgressEvent> = eventEmitter.uploadProgress

    val customBusinessEvents: SharedFlow<CustomBusinessEvent> = eventEmitter.customBusinessEvents

    val serviceEvents: SharedFlow<ServiceEvent> = eventEmitter.serviceEvents

    private var initialized = false
    private var initConfig: InitConfig? = null
    private var authData: AuthCacheData? = null

    private val loginUserId: () -> String? = { authData?.userID }

    val conversationManager =
        ConversationManager(apiService, databaseService, eventEmitter, loginUserId)
    val friendshipManager = FriendshipManager(apiService, eventEmitter, loginUserId)
    val messageManager = MessageManager(
        apiService,
        databaseService,
        webSocketService,
        msgSyncer,
        notificationDispatcher,
        eventEmitter,
        loginUserId,
    )
    val groupManager = GroupManager(apiService, eventEmitter, loginUserId)
    val userManager = UserManager(apiService, databaseService, eventEmitter, loginUserId)
    val momentsManager = MomentsManager(apiService, eventEmitter, loginUserId)
    val favoriteManager = FavoriteManager(apiService, eventEmitter)
    val callManager = CallManager(apiService, messageManager, eventEmitter)

    val redPacketManager = RedPacketManager(apiService, eventEmitter)
    val reportAppealManager = ReportAppealManager(apiService)
    val applicationManager = ApplicationManager(apiService)

    private val log = SdkLogger.tag("IMManager")

    val isInitialized: Boolean get() = initialized

    suspend fun initSDK(config: InitConfig): Boolean = withContext(ioDispatcher) {
        if (initialized) return@withContext true
        try {
            configureLogging(config.logLevel)
            httpClient.initIm(config.apiAddr)
            if (config.authAddr.isNotEmpty()) httpClient.initChat(config.authAddr)
            val adminUrl = config.adminAddr?.takeIf { it.isNotEmpty() } ?: config.authAddr
            if (adminUrl.isNotEmpty()) httpClient.initAdmin(adminUrl)
            msgSyncer.start()
            initConfig = config
            initialized = true
            log.info { "initSDK completed: api=${config.apiAddr}, ws=${config.wsAddr}" }
            true
        } catch (e: Exception) {
            log.error(e) { "initSDK failed" }
            throw e
        }
    }

    suspend fun login(userId: String, token: String): UserInfo = withContext(ioDispatcher) {
        ensureInitialized()
        if (loginStatus.value == LoginStatus.LOGGED) {
            throw XueHuaException.from(SdkErrorCode.ALREADY_LOGIN)
        }
        eventEmitter.setLoginStatus(LoginStatus.LOGGING)
        httpClient.setImToken(token)
        val chatToken = httpClient.getChatToken().takeIf { it.isNotEmpty() }
        authData = AuthCacheData(userID = userId, imToken = token, chatToken = chatToken)
        loginUserIdHolder.userId = userId
        callManager.setCurrentUserId(userId)
        databaseService.switchSpace(userId)
        msgSyncer.bindUser(userId)
        msgSyncer.bindCallManager(callManager)
        val users = userManager.getUsersInfo(listOf(userId))
        val user = users.firstOrNull() ?: UserInfo(userID = userId)
        eventEmitter.setLoginStatus(LoginStatus.LOGGED)
        webSocketService.connect(userId, token)
        user
    }

    suspend fun loginByAccount(account: String, password: String): UserInfo =
        withContext(ioDispatcher) {
            try {
                ensureInitialized()
                eventEmitter.setLoginStatus(LoginStatus.LOGGING)
                val resp = apiService.login(
                    LoginReq(
                        account = account,
                        password = md5Hex(password),
                        platform = loginPlatformId(),
                    ),
                )
                httpClient.setChatToken(resp.chatToken)
                httpClient.setImToken(resp.imToken ?: resp.chatToken)
                login(resp.userID, resp.imToken ?: resp.chatToken)
            } catch (e: Exception) {
                log.error(e) { "loginByAccount failed: account=$account" }
                eventEmitter.setLoginStatus(LoginStatus.LOGOUT)
                throw e
            }
        }

    suspend fun loginByEmail(email: String, password: String): UserInfo =
        withContext(ioDispatcher) {
            ensureInitialized()
            val resp = apiService.loginByEmail(
                LoginReq(
                    email = email,
                    password = md5Hex(password),
                    platform = loginPlatformId(),
                ),
            )
            httpClient.setChatToken(resp.chatToken)
            login(resp.userID, resp.imToken ?: resp.chatToken)
        }

    suspend fun register(
        nickname: String,
        password: String,
        verificationCode: String,
        deviceID: String,
        email: String? = null,
        phoneNumber: String? = null,
        areaCode: String? = null,
        faceURL: String? = null,
        birth: Long = 0,
        gender: Int = 1,
        autoLogin: Boolean = true,
    ): AuthCacheData? = withContext(ioDispatcher) {
        ensureInitialized()
        userManager.register(
            nickname = nickname,
            password = password,
            verificationCode = verificationCode,
            deviceID = deviceID,
            platform = loginPlatformId(),
            email = email,
            phoneNumber = phoneNumber,
            areaCode = areaCode,
            faceURL = faceURL,
            birth = birth,
            gender = gender,
            autoLogin = autoLogin,
        )
    }

    suspend fun loginByPhone(
        areaCode: String,
        phoneNumber: String,
        password: String? = null,
        verifyCode: String? = null,
    ): UserInfo = withContext(ioDispatcher) {
        require(password != null || verifyCode != null) { "password and verifyCode must provide one" }
        ensureInitialized()
        val req = LoginReq(
            areaCode = areaCode,
            phoneNumber = phoneNumber,
            password = password?.let { md5Hex(it) },
            verifyCode = verifyCode,
            platform = loginPlatformId(),
        )
        val resp = apiService.loginByPhone(req)
        httpClient.setChatToken(resp.chatToken)
        login(resp.userID, resp.imToken ?: resp.chatToken)
    }

    suspend fun checkToken(token: String): TokenCheckResult = withContext(ioDispatcher) {
        ensureInitialized()
        try {
            val response = apiService.parseToken(token)
            if (response.isSuccess) TokenCheckResult.Valid else TokenCheckResult.Invalid
        } catch (e: Exception) {
            log.warn(e) { "checkToken failed" }
            TokenCheckResult.NetworkError
        }
    }

    suspend fun loginWithCachedAuth(data: AuthCacheData): UserInfo = withContext(ioDispatcher) {
        httpClient.setChatToken(data.chatToken.orEmpty())
        login(data.userID, data.imToken)
    }

    suspend fun logout() = withContext(ioDispatcher) {
        callManager.dispose()
        webSocketService.disconnect()
        authData = null
        loginUserIdHolder.userId = null
        httpClient.setImToken("")
        httpClient.setChatToken("")
        eventEmitter.setLoginStatus(LoginStatus.LOGOUT)
        eventEmitter.setConnectionState(ConnectionState.DISCONNECTED)
    }

    suspend fun uploadFile(path: String, onProgress: ((Int) -> Unit)? = null): UploadFileResult =
        withContext(ioDispatcher) {
            ensureLoggedIn()
            uploadFileBytes(fileSystem.readBytes(path), path.substringAfterLast('/'), onProgress)
        }

    suspend fun uploadFileBytes(
        bytes: ByteArray,
        fileName: String,
        onProgress: ((Int) -> Unit)? = null,
    ): UploadFileResult = withContext(ioDispatcher) {
        ensureLoggedIn()
        fileUploadService.uploadFileBytes(bytes, fileName, onProgress)
    }

    fun getLoginUserId(): String? = authData?.userID

    fun getAuthCacheData(): AuthCacheData? = authData

    private fun ensureInitialized() {
        if (!initialized) {
            log.error { "SDK not initialized — call initSDK() before using IM APIs" }
            throw XueHuaException.from(SdkErrorCode.NOT_INIT)
        }
    }

    private fun ensureLoggedIn() {
        if (loginStatus.value != LoginStatus.LOGGED) throw XueHuaException.from(SdkErrorCode.NOT_LOGIN)
    }

    private fun configureLogging(level: LogLevel) {
        configureSdkLogging(level)
    }

    private fun loginPlatformId(): Int =
        initConfig?.resolvedPlatformId() ?: currentPlatform().value

    companion object {
        lateinit var instance: IMManager

        internal fun create(
            eventEmitter: SdkEventEmitter,
            driverFactory: DatabaseDriverFactory,
            fileSystem: FileSystem,
            gzipCodec: GzipCodec,
            config: InitConfig? = null,
        ): IMManager {
            val httpClient = SdkHttpClient()
            val apiService = ImApiService(httpClient)
            val dbPath = config?.dbPath ?: defaultDbPath(config?.dbName ?: "openim.db")
            val databaseService = DatabaseService(driverFactory, dbPath)
            val wsHttpClient = createWsHttpClient()
            val webSocketService = WebSocketService(
                wsUrl = config?.wsAddr ?: "",
                platformId = config?.resolvedPlatformId() ?: currentPlatform().value,
                gzipCodec = gzipCodec,
                eventEmitter = eventEmitter,
                httpClient = wsHttpClient,
            )
            val loginUserIdHolder = LoginUserIdHolder()
            val notificationDispatcher = NotificationDispatcher(
                databaseService = databaseService,
                eventEmitter = eventEmitter,
                loginUserId = { loginUserIdHolder.userId },
            )
            val msgSyncer = MsgSyncer(
                webSocketService = webSocketService,
                databaseService = databaseService,
                apiService = apiService,
                notificationDispatcher = notificationDispatcher,
                eventEmitter = eventEmitter,
            )
            val fileUploadService = FileUploadService(
                apiService = apiService,
                httpClient = httpClient,
                fileSystem = fileSystem,
                eventEmitter = eventEmitter,
                loginUserId = { loginUserIdHolder.userId },
            )
            return IMManager(
                eventEmitter = eventEmitter,
                httpClient = httpClient,
                apiService = apiService,
                databaseService = databaseService,
                webSocketService = webSocketService,
                msgSyncer = msgSyncer,
                notificationDispatcher = notificationDispatcher,
                fileSystem = fileSystem,
                loginUserIdHolder = loginUserIdHolder,
                fileUploadService = fileUploadService,
            ).also { instance = it }
        }
    }
}
