package com.kurban.xuehuaim.sdk.sync

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.enum.ConversationType
import com.kurban.xuehuaim.sdk.event.ConversationEvent
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.model.ConversationInfo
import com.kurban.xuehuaim.sdk.model.FriendInfo
import com.kurban.xuehuaim.sdk.model.GroupInfo
import com.kurban.xuehuaim.sdk.network.http.ImApiService
import com.kurban.xuehuaim.sdk.util.SdkLogger
import com.kurban.xuehuaim.sdk.util.peerUserId
import com.kurban.xuehuaim.sdk.util.resolveGroupChatDisplay
import com.kurban.xuehuaim.sdk.util.resolveGroupId
import com.kurban.xuehuaim.sdk.util.resolveSingleChatDisplay

internal object ConversationDisplayEnricher {
    private val log = SdkLogger.tag("ConversationDisplayEnricher")

    suspend fun enrichConversationDisplayNames(
        apiService: ImApiService,
        databaseService: DatabaseService,
        eventEmitter: SdkEventEmitter,
        userId: String,
    ) {
        val needEnrich = databaseService.getAllConversations()
            .filter { it.showName.isNullOrBlank() }
        if (needEnrich.isEmpty()) return

        val friends = runCatching { apiService.getFriendList(userId) }
            .getOrElse { error ->
                log.warn(error) { "load friend list for display enrichment failed" }
                emptyList()
            }
            .associateBy { it.userID }

        val groups = runCatching { apiService.getJoinedGroupList(userId) }
            .getOrElse { error ->
                log.warn(error) { "load joined groups for display enrichment failed" }
                emptyList()
            }
            .associateBy { it.groupID }

        val missingGroupIds = needEnrich
            .filter { it.conversationType == ConversationType.SUPER_GROUP }
            .mapNotNull { resolveGroupId(it) }
            .filter { it !in groups }
            .distinct()
        val extraGroups = if (missingGroupIds.isNotEmpty()) {
            runCatching { apiService.getGroupsInfo(missingGroupIds) }
                .getOrElse { error ->
                    log.warn(error) { "load group info for display enrichment failed" }
                    emptyList()
                }
                .associateBy { it.groupID }
        } else {
            emptyMap()
        }
        val allGroups = groups + extraGroups

        val missingPeerIds = needEnrich
            .filter { it.conversationType == ConversationType.SINGLE }
            .mapNotNull { peerUserId(it, userId) }
            .filter { it !in friends }
            .distinct()
        val users = if (missingPeerIds.isNotEmpty()) {
            runCatching { apiService.getUsersInfo(missingPeerIds) }
                .getOrElse { error ->
                    log.warn(error) { "load users info for display enrichment failed" }
                    emptyList()
                }
                .associateBy { it.userID }
        } else {
            emptyMap()
        }

        val changed = mutableListOf<ConversationInfo>()
        needEnrich.forEach { conversation ->
            val enriched = when (conversation.conversationType) {
                ConversationType.SINGLE -> resolveSingleChatDisplay(
                    conversation = conversation,
                    friends = friends,
                    users = users,
                    selfUserId = userId,
                )

                ConversationType.SUPER_GROUP -> resolveGroupChatDisplay(
                    conversation = conversation,
                    groups = allGroups,
                )

                else -> conversation
            }
            if (enriched.showName != conversation.showName || enriched.faceURL != conversation.faceURL) {
                databaseService.insertOrReplaceConversation(enriched)
                if ((enriched.latestMsgSendTime ?: 0) > 0) {
                    changed.add(enriched)
                }
            }
        }
        changed.forEach { eventEmitter.emitConversation(ConversationEvent.Changed(it)) }
        if (changed.isNotEmpty()) {
            log.info { "enriched display names for ${changed.size} conversations" }
        }
    }

    suspend fun updateSingleChatDisplay(
        databaseService: DatabaseService,
        eventEmitter: SdkEventEmitter,
        friend: FriendInfo,
        selfUserId: String,
    ) {
        val conversations = databaseService.getAllConversations().filter {
            it.conversationType == ConversationType.SINGLE &&
                    peerUserId(it, selfUserId) == friend.userID
        }
        conversations.forEach { conversation ->
            val updated = conversation.copy(
                showName = friend.getShowName(),
                faceURL = friend.faceURL?.takeIf { it.isNotBlank() } ?: conversation.faceURL,
                userID = friend.userID,
            )
            databaseService.insertOrReplaceConversation(updated)
            if ((updated.latestMsgSendTime ?: 0) > 0) {
                eventEmitter.emitConversation(ConversationEvent.Changed(updated))
            }
        }
    }

    suspend fun updateGroupChatDisplay(
        databaseService: DatabaseService,
        eventEmitter: SdkEventEmitter,
        group: GroupInfo,
    ) {
        val conversations = databaseService.getAllConversations().filter {
            it.conversationType == ConversationType.SUPER_GROUP &&
                    (it.groupID == group.groupID || it.conversationID == "sg_${group.groupID}")
        }
        conversations.forEach { conversation ->
            val updated = conversation.copy(
                showName = group.groupName?.takeIf { it.isNotBlank() } ?: conversation.showName,
                faceURL = group.faceURL?.takeIf { it.isNotBlank() } ?: conversation.faceURL,
                groupID = group.groupID,
            )
            databaseService.insertOrReplaceConversation(updated)
            if ((updated.latestMsgSendTime ?: 0) > 0) {
                eventEmitter.emitConversation(ConversationEvent.Changed(updated))
            }
        }
    }
}
