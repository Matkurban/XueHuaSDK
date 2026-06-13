package com.kurban.xuehuaim.sdk.db

import kotlinx.serialization.json.Json

internal object VersionSyncUidListCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(uidList: List<String>): String? =
        if (uidList.isEmpty()) null else json.encodeToString(uidList)

    fun decode(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
    }
}
