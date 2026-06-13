package com.kurban.xuehuaim.sdk.manager

import com.kurban.xuehuaim.sdk.model.ApplicationVersionInfo
import com.kurban.xuehuaim.sdk.network.http.ImApiService


class ApplicationManager internal constructor(
    private val apiService: ImApiService,
) {
    suspend fun checkVersion(): ApplicationVersionInfo = apiService.checkAppVersion()

    suspend fun getLatestVersion(
        platform: String,
        currentVersion: String? = null,
    ) = getLatestVersion(apiService, platform, currentVersion)

    suspend fun getLatestVersion(currentVersion: String? = null) =
        getLatestVersion(apiService, currentVersion)
}
