package com.kurban.xuehuaim.sdk.manager

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.db.SendingMessage
import com.kurban.xuehuaim.sdk.enum.ConnectionState
import com.kurban.xuehuaim.sdk.enum.ConversationType
import com.kurban.xuehuaim.sdk.enum.GroupAtType
import com.kurban.xuehuaim.sdk.enum.MessageStatus
import com.kurban.xuehuaim.sdk.enum.MessageType
import com.kurban.xuehuaim.sdk.enum.ReceiveMessageOpt
import com.kurban.xuehuaim.sdk.enum.SdkErrorCode
import com.kurban.xuehuaim.sdk.exception.XueHuaException
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.model.AppealInfo
import com.kurban.xuehuaim.sdk.model.ApplicationVersionInfo
import com.kurban.xuehuaim.sdk.model.AtTextElem
import com.kurban.xuehuaim.sdk.model.AtUserInfo
import com.kurban.xuehuaim.sdk.model.AuthCacheData
import com.kurban.xuehuaim.sdk.model.BlacklistInfo
import com.kurban.xuehuaim.sdk.model.ConversationInfo
import com.kurban.xuehuaim.sdk.model.ConversationReq
import com.kurban.xuehuaim.sdk.model.CustomElem
import com.kurban.xuehuaim.sdk.model.FavoriteItem
import com.kurban.xuehuaim.sdk.model.FileElem
import com.kurban.xuehuaim.sdk.model.FriendApplicationInfo
import com.kurban.xuehuaim.sdk.model.FriendInfo
import com.kurban.xuehuaim.sdk.model.GroupApplicationInfo
import com.kurban.xuehuaim.sdk.model.GroupInfo
import com.kurban.xuehuaim.sdk.model.GroupMemberInfo
import com.kurban.xuehuaim.sdk.model.MergeElem
import com.kurban.xuehuaim.sdk.model.Message
import com.kurban.xuehuaim.sdk.model.MomentCommentWithUser
import com.kurban.xuehuaim.sdk.model.MomentInfo
import com.kurban.xuehuaim.sdk.model.MomentListResponse
import com.kurban.xuehuaim.sdk.model.PictureElem
import com.kurban.xuehuaim.sdk.model.PictureInfo
import com.kurban.xuehuaim.sdk.model.PointsTransaction
import com.kurban.xuehuaim.sdk.model.QuoteElem
import com.kurban.xuehuaim.sdk.model.RedPacketDetail
import com.kurban.xuehuaim.sdk.model.SearchParams
import com.kurban.xuehuaim.sdk.model.SendRedPacketRequest
import com.kurban.xuehuaim.sdk.model.ReportInfo
import com.kurban.xuehuaim.sdk.model.SearchResult
import com.kurban.xuehuaim.sdk.model.SoundElem
import com.kurban.xuehuaim.sdk.model.TextElem
import com.kurban.xuehuaim.sdk.model.UserFullInfo
import com.kurban.xuehuaim.sdk.model.UserInfo
import com.kurban.xuehuaim.sdk.model.UserStatusInfo
import com.kurban.xuehuaim.sdk.model.VideoElem
import com.kurban.xuehuaim.sdk.network.http.ImApiService
import com.kurban.xuehuaim.sdk.network.http.LoginUserIdProvider
import com.kurban.xuehuaim.sdk.network.notify.NotificationDispatcher
import com.kurban.xuehuaim.sdk.network.sync.MsgSyncer
import com.kurban.xuehuaim.sdk.network.sync.SendMsgReqData
import com.kurban.xuehuaim.sdk.network.sync.decodeUserSendMsgResp
import com.kurban.xuehuaim.sdk.network.sync.encodeSendMsgReq
import com.kurban.xuehuaim.sdk.network.ws.WebSocketService
import com.kurban.xuehuaim.sdk.network.ws.WsIdentifier
import com.kurban.xuehuaim.sdk.network.ws.WsRequest
import com.kurban.xuehuaim.sdk.platform.currentPlatform
import com.kurban.xuehuaim.sdk.platform.FileSystem
import com.kurban.xuehuaim.sdk.platform.ioDispatcher
import com.kurban.xuehuaim.sdk.platform.sdkScope
import com.kurban.xuehuaim.sdk.sync.FavoriteSync
import com.kurban.xuehuaim.sdk.sync.FriendSync
import com.kurban.xuehuaim.sdk.sync.GroupSync
import com.kurban.xuehuaim.sdk.sync.MessageDisplayEnricher
import com.kurban.xuehuaim.sdk.sync.MomentSync
import com.kurban.xuehuaim.sdk.util.ClientMsgIdGenerator
import com.kurban.xuehuaim.sdk.util.ConversationMessageUpdater
import com.kurban.xuehuaim.sdk.util.OpenImUtils
import com.kurban.xuehuaim.sdk.util.md5Hex
import com.kurban.xuehuaim.sdk.util.withParsedContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds


class RedPacketManager internal constructor(
    private val apiService: ImApiService,
    private val databaseService: DatabaseService,
    private val eventEmitter: SdkEventEmitter,
) {
    private var cachedBalance: Double = 0.0
    val cachedBalanceValue: Double get() = cachedBalance
    private val grabbedPacketIds = mutableSetOf<String>()

    suspend fun getPointsBalance(): Double = withContext(ioDispatcher) {
        cachedBalance = ((apiService.getPointsBalance() * 100).toLong() / 100.0)
        cachedBalance
    }

    suspend fun getPointsTransactions(
        pageNumber: Int = 1,
        showNumber: Int = 20,
        txType: Int? = null,
    ): Pair<Int, List<PointsTransaction>> = withContext(ioDispatcher) {
        apiService.getPointsTransactions(pageNumber, showNumber, txType)
    }

    suspend fun getIncomeTransactions(
        pageNumber: Int = 1,
        showNumber: Int = 20,
    ): Pair<Int, List<PointsTransaction>> = withContext(ioDispatcher) {
        val (total, items) = getPointsTransactions(pageNumber, showNumber)
        total to items.filter { it.isIncome }
    }

    suspend fun getExpenseTransactions(
        pageNumber: Int = 1,
        showNumber: Int = 20,
    ): Pair<Int, List<PointsTransaction>> = withContext(ioDispatcher) {
        val (total, items) = getPointsTransactions(pageNumber, showNumber)
        total to items.filter { it.isExpense }
    }

    suspend fun sendRedPacket(req: SendRedPacketRequest): String = withContext(ioDispatcher) {
        val packetId = apiService.sendRedPacket(req)
        cachedBalance = ((cachedBalance - req.totalAmount) * 100).toLong() / 100.0
        packetId
    }

    suspend fun grabRedPacket(packetId: String): Double = withContext(ioDispatcher) {
        val amount = apiService.grabRedPacket(packetId)
        cachedBalance = ((cachedBalance + amount) * 100).toLong() / 100.0
        markGrabbed(packetId)
        eventEmitter.emitRedPacket(
            com.kurban.xuehuaim.sdk.event.RedPacketEvent.Grabbed(packetId, amount),
        )
        amount
    }

    suspend fun getRedPacketDetail(packetId: String): RedPacketDetail =
        withContext(ioDispatcher) {
            apiService.getRedPacketDetail(packetId)
        }

    fun isGrabbed(packetId: String): Boolean = grabbedPacketIds.contains(packetId)

    suspend fun preloadGrabbedStatus(packetIds: List<String>) = withContext(ioDispatcher) {
        if (packetIds.isEmpty()) return@withContext
        val uncached = packetIds.filter { !grabbedPacketIds.contains(it) }
        if (uncached.isEmpty()) return@withContext
        val grabbed = databaseService.selectGrabbedRedPacketIds(uncached)
        grabbedPacketIds.addAll(grabbed)
    }

    suspend fun markGrabbed(packetId: String) = withContext(ioDispatcher) {
        grabbedPacketIds.add(packetId)
        databaseService.markRedPacketGrabbed(packetId)
    }
}
