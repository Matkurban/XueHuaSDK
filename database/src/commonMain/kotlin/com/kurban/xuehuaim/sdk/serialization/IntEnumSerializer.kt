package com.kurban.xuehuaim.sdk.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

fun <T : Enum<T>> intEnumSerializer(
    serialName: String,
    values: Array<T>,
    valueOf: (T) -> Int,
    unknownFallback: (Int) -> T,
): KSerializer<T> = object : KSerializer<T> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(serialName, PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: T) {
        encoder.encodeInt(valueOf(value))
    }

    override fun deserialize(decoder: Decoder): T {
        val intValue = decoder.decodeInt()
        return values.find { valueOf(it) == intValue } ?: unknownFallback(intValue)
    }
}
