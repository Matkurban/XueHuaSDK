package com.kurban.xuehuaim.sdk.manager

import com.kurban.xuehuaim.sdk.model.AppealCaptcha
import com.kurban.xuehuaim.sdk.model.AppealUploadResult
import com.kurban.xuehuaim.sdk.model.CreateReportResult
import com.kurban.xuehuaim.sdk.network.http.ImApiService
import com.kurban.xuehuaim.sdk.platform.ioDispatcher
import kotlinx.coroutines.withContext

internal suspend fun ReportAppealManager.reportUser(
    apiService: ImApiService,
    targetUserId: String,
    category: String,
    description: String? = null,
    evidenceUrls: List<String>? = null,
): CreateReportResult? = withContext(ioDispatcher) {
    runCatching {
        apiService.createReport(
            "user",
            targetUserId,
            category,
            description,
            evidenceUrls = evidenceUrls
        )
    }.getOrNull()
}

internal suspend fun ReportAppealManager.reportGroup(
    apiService: ImApiService,
    targetGroupId: String,
    category: String,
    description: String? = null,
    evidenceUrls: List<String>? = null,
): CreateReportResult? = withContext(ioDispatcher) {
    runCatching {
        apiService.createReport(
            "group",
            targetGroupId,
            category,
            description,
            evidenceUrls = evidenceUrls
        )
    }.getOrNull()
}

internal suspend fun ReportAppealManager.reportMessage(
    apiService: ImApiService,
    targetUserId: String,
    messageId: String,
    category: String,
    description: String? = null,
    evidenceUrls: List<String>? = null,
): CreateReportResult? = withContext(ioDispatcher) {
    runCatching {
        apiService.createReport(
            "message",
            targetUserId,
            category,
            description,
            messageID = messageId,
            evidenceUrls = evidenceUrls,
        )
    }.getOrNull()
}

internal suspend fun ReportAppealManager.getAppealCaptcha(apiService: ImApiService): AppealCaptcha? =
    withContext(ioDispatcher) { runCatching { apiService.requestAppealCaptcha() }.getOrNull() }

internal suspend fun ReportAppealManager.uploadAppealEvidence(
    apiService: ImApiService,
    appealToken: String,
    bytes: ByteArray,
    fileName: String,
): AppealUploadResult? = withContext(ioDispatcher) {
    runCatching { apiService.uploadAppealEvidence(appealToken, bytes, fileName) }.getOrNull()
}
