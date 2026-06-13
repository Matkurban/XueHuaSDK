package com.kurban.xuehuaim.sdk.manager

import com.kurban.xuehuaim.sdk.model.ApplicationVersionInfo
import com.kurban.xuehuaim.sdk.network.http.ImApiService
import com.kurban.xuehuaim.sdk.network.http.applicationPlatformName
import com.kurban.xuehuaim.sdk.platform.ioDispatcher
import kotlinx.coroutines.withContext

internal suspend fun getLatestVersion(
    apiService: ImApiService,
    platform: String,
    currentVersion: String? = null,
): ApplicationVersionInfo? = withContext(ioDispatcher) {
    apiService.getLatestApplicationVersion(platform, currentVersion)
}

internal suspend fun getLatestVersion(
    apiService: ImApiService,
    currentVersion: String? = null,
): ApplicationVersionInfo? =
    getLatestVersion(apiService, applicationPlatformName(), currentVersion)
