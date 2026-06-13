package com.kurban.xuehuaim.sdk.manager

import com.kurban.xuehuaim.sdk.model.AppealInfo
import com.kurban.xuehuaim.sdk.model.ReportInfo
import com.kurban.xuehuaim.sdk.network.http.ImApiService


class ReportAppealManager internal constructor(
    private val apiService: ImApiService,
) {
    suspend fun submitReport(report: ReportInfo): ReportInfo = apiService.submitReport(report)
    suspend fun submitAppeal(appeal: AppealInfo): AppealInfo = apiService.submitAppeal(appeal)

    suspend fun reportUser(
        targetUserId: String,
        category: String,
        description: String? = null,
        evidenceUrls: List<String>? = null,
    ) = reportUser(apiService, targetUserId, category, description, evidenceUrls)

    suspend fun reportGroup(
        targetGroupId: String,
        category: String,
        description: String? = null,
        evidenceUrls: List<String>? = null,
    ) = reportGroup(apiService, targetGroupId, category, description, evidenceUrls)

    suspend fun reportMessage(
        targetUserId: String,
        messageId: String,
        category: String,
        description: String? = null,
        evidenceUrls: List<String>? = null,
    ) = reportMessage(apiService, targetUserId, messageId, category, description, evidenceUrls)

    suspend fun getAppealCaptcha() = getAppealCaptcha(apiService)

    suspend fun uploadAppealEvidence(appealToken: String, bytes: ByteArray, fileName: String) =
        uploadAppealEvidence(apiService, appealToken, bytes, fileName)
}
