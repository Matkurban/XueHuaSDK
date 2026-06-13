package com.kurban.xuehuaim.sdk.network.ws

import com.kurban.xuehuaim.sdk.platform.GzipCodec
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
internal class WsCodec(
    private val gzipCodec: GzipCodec,
    val enableCompression: Boolean = gzipCodec.isSupported,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun encodeRequest(request: WsRequest): ByteArray {
        val payload = json.encodeToString(
            WsRequestJson(
                reqIdentifier = request.reqIdentifier,
                token = request.token,
                sendID = request.sendID,
                operationID = request.operationID,
                msgIncr = request.msgIncr,
                data = if (request.data.isNotEmpty()) Base64.encode(request.data) else "",
            ),
        ).encodeToByteArray()
        return if (enableCompression) gzipCodec.compress(payload) else payload
    }

    fun decodeResponse(data: ByteArray): WsResponse {
        val decompressed = if (enableCompression) gzipCodec.decompress(data) else data
        val parsed = json.decodeFromString<WsResponseJson>(decompressed.decodeToString())
        val dataBytes =
            parsed.data?.takeIf { it.isNotEmpty() }?.let { Base64.decode(it) } ?: ByteArray(0)
        return WsResponse(
            reqIdentifier = parsed.reqIdentifier,
            errCode = parsed.errCode,
            errMsg = parsed.errMsg,
            msgIncr = parsed.msgIncr,
            operationID = parsed.operationID,
            data = dataBytes,
        )
    }
}
