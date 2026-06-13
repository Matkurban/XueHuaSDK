package com.kurban.xuehuaim.sdk.network.http

import com.kurban.xuehuaim.sdk.exception.XueHuaException
import com.kurban.xuehuaim.sdk.model.ApiResponse
import com.kurban.xuehuaim.sdk.util.OperationIdGenerator
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal class SdkHttpClient {
    private val client: HttpClient = createHttpEngine().config {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true })
        }
    }

    private var imBaseUrl: String = ""
    private var chatBaseUrl: String = ""
    private var adminBaseUrl: String = ""
    private var imToken: String = ""
    private var chatToken: String = ""

    fun initIm(baseUrl: String) {
        imBaseUrl = baseUrl.trimEnd('/')
    }

    fun initChat(baseUrl: String) {
        chatBaseUrl = baseUrl.trimEnd('/')
    }

    fun initAdmin(baseUrl: String) {
        adminBaseUrl = baseUrl.trimEnd('/')
    }

    fun setImToken(token: String) {
        imToken = token
    }

    fun setChatToken(token: String) {
        chatToken = token
    }

    fun getChatToken(): String = chatToken

    suspend inline fun <reified Req, reified Res> imPostEnvelope(path: String, body: Req): Res {
        val resp: ApiResponse<Res> = imPost(path, body)
        return resp.unwrap()
    }

    suspend inline fun <reified Req> imPostVoid(path: String, body: Req) {
        val resp: ApiResponse<Unit?> = imPost(path, body)
        resp.unwrapVoid()
    }

    suspend inline fun <reified Req, reified Res> chatPostEnvelope(path: String, body: Req): Res {
        val resp: ApiResponse<Res> = chatPost(path, body)
        return resp.unwrap()
    }

    suspend inline fun <reified Req> chatPostVoid(path: String, body: Req) {
        val resp: ApiResponse<Unit?> = chatPost(path, body)
        resp.unwrapVoid()
    }

    suspend inline fun <reified Req, reified Res> adminPostEnvelope(path: String, body: Req): Res {
        val resp: ApiResponse<Res> = adminPost(path, body)
        return resp.unwrap()
    }

    suspend inline fun <reified Req> adminPostVoid(path: String, body: Req) {
        val resp: ApiResponse<Unit?> = adminPost(path, body)
        resp.unwrapVoid()
    }

    suspend inline fun <reified T> imGet(path: String): T = client.get("$imBaseUrl$path") {
        applyImHeaders()
    }.body()

    suspend inline fun <reified Req, reified Res> imPost(path: String, body: Req): Res =
        client.post("$imBaseUrl$path") {
            applyImHeaders()
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    suspend inline fun <reified Req, reified Res> chatPost(path: String, body: Req): Res =
        client.post("$chatBaseUrl$path") {
            applyChatHeaders()
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    suspend inline fun <reified Req, reified Res> adminPost(path: String, body: Req): Res =
        client.post("$adminBaseUrl$path") {
            applyAdminHeaders()
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    suspend inline fun <reified Req, reified Res> imPut(path: String, body: Req): Res =
        client.put("$imBaseUrl$path") {
            applyImHeaders()
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    suspend inline fun <reified Res> imDelete(path: String): Res =
        client.delete("$imBaseUrl$path") {
            applyImHeaders()
        }.body()

    suspend fun putBytes(url: String, bytes: ByteArray, headers: Map<String, String> = emptyMap()) {
        client.put(url) {
            headers.forEach { (key, value) -> header(key, value) }
            setBody(bytes)
        }
    }

    private fun HttpRequestBuilder.applyImHeaders() {
        header("token", imToken)
        header("operationID", OperationIdGenerator.generate())
    }

    private fun HttpRequestBuilder.applyChatHeaders() {
        header("token", chatToken)
        header("operationID", OperationIdGenerator.generate())
    }

    private fun HttpRequestBuilder.applyAdminHeaders() {
        header("operationID", OperationIdGenerator.generate())
    }

    fun close() {
        client.close()
    }
}

internal suspend fun <T> ApiResponse<T>.unwrap(): T {
    if (!isSuccess) throw XueHuaException(
        code = errCode,
        message = errMsg.ifBlank { errDlt ?: "request failed" })
    return data ?: throw XueHuaException(code = errCode, message = "empty response data")
}

internal fun ApiResponse<*>.unwrapVoid() {
    if (!isSuccess) throw XueHuaException(
        code = errCode,
        message = errMsg.ifBlank { errDlt ?: "request failed" })
}

internal expect fun createHttpEngine(): HttpClient
