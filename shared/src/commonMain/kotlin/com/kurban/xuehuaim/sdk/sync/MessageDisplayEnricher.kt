package com.kurban.xuehuaim.sdk.sync

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.model.Message
import com.kurban.xuehuaim.sdk.network.http.ImApiService
import com.kurban.xuehuaim.sdk.util.SdkLogger

internal object MessageDisplayEnricher {
    private val log = SdkLogger.tag("MessageDisplayEnricher")

    suspend fun enrichMessages(
        apiService: ImApiService,
        databaseService: DatabaseService,
        messages: List<Message>,
    ): List<Message> {
        if (messages.isEmpty()) return messages
        val users = databaseService.getAllUsers().associateBy { it.userID }
        val missingSenderIds = messages
            .filter { it.senderFaceUrl.isNullOrBlank() || it.senderNickname.isNullOrBlank() }
            .mapNotNull { it.sendID }
            .filter { it !in users }
            .distinct()
        val fetchedUsers = if (missingSenderIds.isNotEmpty()) {
            runCatching { apiService.getUsersInfo(missingSenderIds) }
                .getOrElse { error ->
                    log.warn(error) { "load users for message sender enrichment failed" }
                    emptyList()
                }
                .onEach { databaseService.insertOrReplaceUser(it) }
                .associateBy { it.userID }
        } else {
            emptyMap()
        }
        val allUsers = users + fetchedUsers
        return messages.map { message ->
            enrichSingle(message, allUsers)
        }
    }

    private fun enrichSingle(
        message: Message,
        users: Map<String, com.kurban.xuehuaim.sdk.model.UserInfo>
    ): Message {
        val senderId = message.sendID ?: return message
        val user = users[senderId] ?: return message
        val faceUrl = message.senderFaceUrl?.takeIf { it.isNotBlank() } ?: user.faceURL
        val nickname = message.senderNickname?.takeIf { it.isNotBlank() } ?: user.nickname
        if (faceUrl == message.senderFaceUrl && nickname == message.senderNickname) return message
        return message.copy(senderFaceUrl = faceUrl, senderNickname = nickname)
    }
}
