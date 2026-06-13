package com.kurban.xuehuaim.sdk.db

data class VersionSyncInfo(
    val versionID: String,
    val version: Int,
    val uidList: List<String> = emptyList(),
)
