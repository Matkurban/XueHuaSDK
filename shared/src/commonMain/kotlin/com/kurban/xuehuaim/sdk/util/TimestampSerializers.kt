package com.kurban.xuehuaim.sdk.util

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

internal fun parseTimestampMillis(value: String): Long? {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null
    trimmed.toLongOrNull()?.let { return it }
    runCatching { kotlin.time.Instant.parse(trimmed).toEpochMilliseconds() }.getOrNull()
        ?.let { return it }
    val normalized = trimmed.replace(' ', 'T')
    runCatching {
        LocalDateTime.parse(normalized).toInstant(TimeZone.UTC).toEpochMilliseconds()
    }.getOrNull()?.let { return it }
    return null
}

object FlexibleNullableTimestampSerializer : KSerializer<Long?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleNullableTimestamp", PrimitiveKind.LONG)

    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): Long? {
        val jsonDecoder = decoder as? JsonDecoder
        if (jsonDecoder != null) {
            return when (val element = jsonDecoder.decodeJsonElement()) {
                JsonNull -> null
                is JsonPrimitive -> when {
                    element.isString -> parseTimestampMillis(element.content)
                    else -> element.longOrNull
                }

                else -> null
            }
        }
        return decoder.decodeNullableSerializableValue(Long.serializer())
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: Long?) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            encoder.encodeLong(value)
        }
    }
}

object FlexibleTimestampSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleTimestamp", PrimitiveKind.LONG)

    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): Long {
        val jsonDecoder = decoder as? JsonDecoder
        if (jsonDecoder != null) {
            return when (val element = jsonDecoder.decodeJsonElement()) {
                JsonNull -> 0L
                is JsonPrimitive -> when {
                    element.isString -> parseTimestampMillis(element.content) ?: 0L
                    else -> element.longOrNull ?: 0L
                }

                else -> 0L
            }
        }
        return decoder.decodeLong()
    }

    override fun serialize(encoder: Encoder, value: Long) {
        encoder.encodeLong(value)
    }
}
