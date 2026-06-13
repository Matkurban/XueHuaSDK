package com.kurban.xuehuaim.sdk.util

import com.kurban.xuehuaim.sdk.enum.MessageType
import com.kurban.xuehuaim.sdk.model.AtTextElem
import com.kurban.xuehuaim.sdk.model.CallSignalElem
import com.kurban.xuehuaim.sdk.model.CustomElem
import com.kurban.xuehuaim.sdk.model.FileElem
import com.kurban.xuehuaim.sdk.model.MergeElem
import com.kurban.xuehuaim.sdk.model.Message
import com.kurban.xuehuaim.sdk.model.PictureElem
import com.kurban.xuehuaim.sdk.model.QuoteElem
import com.kurban.xuehuaim.sdk.model.RedPacketElem
import com.kurban.xuehuaim.sdk.model.SoundElem
import com.kurban.xuehuaim.sdk.model.TextElem
import com.kurban.xuehuaim.sdk.model.VideoElem
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

fun Message.withParsedContent(): Message {
    val rawContent = content?.takeIf { it.isNotBlank() } ?: return this
    val type = contentType ?: return this

    return when (type) {
        MessageType.TEXT,
        MessageType.ADVANCED_TEXT,
        MessageType.MARKDOWN_TEXT,
            -> if (textElem != null) {
            this
        } else {
            decodeOrSelf { copy(textElem = json.decodeFromString<TextElem>(rawContent)) }
        }

        MessageType.PICTURE -> if (pictureElem != null) {
            this
        } else {
            decodeOrSelf { copy(pictureElem = json.decodeFromString<PictureElem>(rawContent)) }
        }

        MessageType.VOICE -> if (soundElem != null) {
            this
        } else {
            decodeOrSelf { copy(soundElem = json.decodeFromString<SoundElem>(rawContent)) }
        }

        MessageType.VIDEO -> if (videoElem != null) {
            this
        } else {
            decodeOrSelf { copy(videoElem = json.decodeFromString<VideoElem>(rawContent)) }
        }

        MessageType.FILE -> if (fileElem != null) {
            this
        } else {
            decodeOrSelf { copy(fileElem = json.decodeFromString<FileElem>(rawContent)) }
        }

        MessageType.AT_TEXT -> if (atTextElem != null) {
            this
        } else {
            decodeOrSelf { copy(atTextElem = json.decodeFromString<AtTextElem>(rawContent)) }
        }

        MessageType.QUOTE -> if (quoteElem != null) {
            this
        } else {
            decodeOrSelf {
                val elem = json.decodeFromString<QuoteElem>(rawContent)
                copy(
                    quoteElem = elem,
                    textElem = textElem ?: elem.text?.let(::TextElem),
                )
            }
        }

        MessageType.CUSTOM -> if (customElem != null) {
            this
        } else {
            decodeOrSelf { copy(customElem = json.decodeFromString<CustomElem>(rawContent)) }
        }

        MessageType.CALL_SIGNAL -> if (callSignalElem != null) {
            this
        } else {
            decodeOrSelf { copy(callSignalElem = json.decodeFromString<CallSignalElem>(rawContent)) }
        }

        MessageType.RED_PACKET,
        MessageType.RED_PACKET_GRAB_NOTIFY,
            -> if (redPacketElem != null) {
            this
        } else {
            decodeOrSelf { copy(redPacketElem = json.decodeFromString<RedPacketElem>(rawContent)) }
        }

        MessageType.MERGER -> if (mergeElem != null) {
            this
        } else {
            decodeOrSelf {
                val elem = json.decodeFromString<MergeElem>(rawContent)
                copy(mergeElem = elem.copy(multiMessage = elem.multiMessage?.map { it.withParsedContent() }))
            }
        }

        else -> this
    }
}

private inline fun Message.decodeOrSelf(block: () -> Message): Message =
    runCatching(block).getOrElse { this }
