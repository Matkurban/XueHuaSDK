package com.kurban.xuehuaim.sdk.manager

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.model.PointsTransaction
import com.kurban.xuehuaim.sdk.model.RedPacketDetail
import com.kurban.xuehuaim.sdk.model.SendRedPacketRequest
import com.kurban.xuehuaim.sdk.network.http.ImApiService
import com.kurban.xuehuaim.sdk.platform.ioDispatcher
import kotlinx.coroutines.withContext


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
