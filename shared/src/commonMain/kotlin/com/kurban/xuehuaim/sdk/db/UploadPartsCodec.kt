package com.kurban.xuehuaim.sdk.db

import kotlinx.serialization.json.Json

internal object UploadPartsCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(parts: List<Int>): String? {
        if (parts.isEmpty()) return null
        return json.encodeToString(parts)
    }

    fun decode(raw: String?): List<Int> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<Int>>(raw) }
            .getOrDefault(emptyList())
            .filter { it > 0 }
    }
}
