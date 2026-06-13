package com.kurban.xuehuaim.sdk.manager

import com.kurban.xuehuaim.sdk.db.DatabaseService
import com.kurban.xuehuaim.sdk.db.UploadPartsCodec
import com.kurban.xuehuaim.sdk.db.UploadRecord
import com.kurban.xuehuaim.sdk.enum.SdkErrorCode
import com.kurban.xuehuaim.sdk.event.MessageEvent
import com.kurban.xuehuaim.sdk.event.UploadProgressEvent
import com.kurban.xuehuaim.sdk.exception.XueHuaException
import com.kurban.xuehuaim.sdk.flow.SdkEventEmitter
import com.kurban.xuehuaim.sdk.model.UploadFileResult
import com.kurban.xuehuaim.sdk.network.http.CompleteMultipartUploadReq
import com.kurban.xuehuaim.sdk.network.http.ImApiService
import com.kurban.xuehuaim.sdk.network.http.InitiateMultipartUploadReq
import com.kurban.xuehuaim.sdk.network.http.PartLimitResp
import com.kurban.xuehuaim.sdk.network.http.SdkHttpClient
import com.kurban.xuehuaim.sdk.network.http.UploadKeyValue
import com.kurban.xuehuaim.sdk.network.http.UploadPartSign
import com.kurban.xuehuaim.sdk.network.http.UploadSignInfo
import com.kurban.xuehuaim.sdk.platform.FileSystem
import com.kurban.xuehuaim.sdk.util.System
import com.kurban.xuehuaim.sdk.util.md5Hex

internal class FileUploadService(
    private val apiService: ImApiService,
    private val httpClient: SdkHttpClient,
    private val fileSystem: FileSystem,
    private val databaseService: DatabaseService,
    private val eventEmitter: SdkEventEmitter,
    private val loginUserId: () -> String?,
) {
    private var partLimitCache: PartLimitResp? = null

    suspend fun uploadFile(
        path: String,
        fileName: String = path.substringAfterLast('/'),
        onProgress: ((Int) -> Unit)? = null,
        clientMsgId: String? = null,
        cause: String = "",
    ): UploadFileResult {
        if (!fileSystem.exists(path)) {
            throw XueHuaException.from(SdkErrorCode.FILE_UPLOAD_FAILED, "file not exist")
        }
        return uploadInternal(
            source = UploadSource.Path(path),
            fileName = fileName,
            fileSize = fileSystem.fileSize(path),
            onProgress = onProgress,
            clientMsgId = clientMsgId,
            cause = cause,
        )
    }

    suspend fun uploadFileBytes(
        bytes: ByteArray,
        fileName: String,
        onProgress: ((Int) -> Unit)? = null,
        clientMsgId: String? = null,
        cause: String = "",
    ): UploadFileResult = uploadInternal(
        source = UploadSource.Bytes(bytes),
        fileName = fileName,
        fileSize = bytes.size.toLong(),
        onProgress = onProgress,
        clientMsgId = clientMsgId,
        cause = cause,
    )

    private suspend fun uploadInternal(
        source: UploadSource,
        fileName: String,
        fileSize: Long,
        onProgress: ((Int) -> Unit)?,
        clientMsgId: String?,
        cause: String,
    ): UploadFileResult {
        val userId = loginUserId() ?: throw XueHuaException.from(SdkErrorCode.NOT_LOGIN)
        val progressId = clientMsgId ?: fileName
        eventEmitter.emitUploadProgress(
            UploadProgressEvent(uploadId = progressId, progress = 0, total = fileSize, current = 0),
        )
        clientMsgId?.let {
            eventEmitter.emitMessage(MessageEvent.SendProgress(clientMsgId = it, progress = 0))
        }

        val partSize = resolvePartSize(fileSize)
        val partNum = ((fileSize + partSize - 1) / partSize).toInt().coerceIn(1, 10000)
        val partSizes = List(partNum) { index ->
            if (index < partNum - 1) partSize.toInt() else (fileSize - partSize * (partNum - 1)).toInt()
        }
        val partMd5s = List(partNum) { index ->
            val start = index * partSize
            val partBytes = readPartBytes(source, start, partSizes[index])
            fileSystem.md5(partBytes)
        }
        val hash = md5Hex(partMd5s.joinToString(","))
        val objectName = buildObjectName(userId, fileName)
        val contentType = guessContentType(fileName)

        val cachedTask = databaseService.getUploadByHashAndName(hash, objectName)
        val cachedUploadId = cachedTask?.uploadID
        val cachedUploadedParts = UploadPartsCodec.decode(cachedTask?.uploadedParts).toSet()

        val initResp = apiService.initiateMultipartUpload(
            InitiateMultipartUploadReq(
                hash = hash,
                size = fileSize,
                partSize = partSize,
                maxParts = partNum.coerceAtMost(20),
                cause = cause,
                name = objectName,
                contentType = contentType,
            ),
        )
        initResp.url?.takeIf { it.isNotEmpty() }?.let { url ->
            onProgress?.invoke(100)
            emitProgress(progressId, clientMsgId, 100, fileSize, fileSize)
            return UploadFileResult(url = url, uuid = hash)
        }

        val upload = initResp.upload ?: throw XueHuaException.from(
            SdkErrorCode.FILE_UPLOAD_FAILED,
            "missing upload info",
        )
        val uploadID = upload.uploadID
        if (uploadID.isBlank()) {
            throw XueHuaException.from(SdkErrorCode.FILE_UPLOAD_FAILED, "missing uploadID")
        }
        upload.partSize?.takeIf { it != partSize }?.let {
            partLimitCache = null
            throw XueHuaException.from(SdkErrorCode.FILE_UPLOAD_FAILED, "part size not match")
        }

        val canResume = !cachedUploadId.isNullOrBlank() && cachedUploadId == uploadID
        val partEtags = arrayOfNulls<String>(partNum)
        var uploadedSize = 0L
        if (canResume) {
            for (part in cachedUploadedParts) {
                val idx = part - 1
                if (idx in 0 until partNum) {
                    partEtags[idx] = partMd5s[idx]
                    uploadedSize += partSizes[idx]
                }
            }
        }

        var uploadRecord = UploadRecord(
            uploadID = uploadID,
            hash = hash,
            name = objectName,
            fileSize = fileSize,
            partSize = partSize,
            partNum = partNum,
            uploadedParts = UploadPartsCodec.encode(
                if (canResume) cachedUploadedParts.sorted() else emptyList(),
            ),
            updateTime = System.currentTimeMillis(),
        )
        databaseService.upsertUploadTask(uploadRecord)

        val sign = upload.sign ?: UploadSignInfo()
        var currentSignParts = sign.parts
        for (index in 0 until partNum) {
            if (partEtags[index] != null) {
                continue
            }

            val currentPartSize = partSizes[index]
            val partNumber = index + 1
            val start = index * partSize
            val partBytes = readPartBytes(source, start, currentPartSize)

            var partInfo = currentSignParts.firstOrNull { it.partNumber == partNumber }
            if (partInfo == null) {
                val nextBatch = buildList {
                    for (offset in 0 until 20) {
                        val number = partNumber + offset
                        if (number <= partNum) add(number)
                    }
                }
                val authResp = apiService.authSign(uploadID, nextBatch)
                currentSignParts = authResp.parts
                partInfo = currentSignParts.firstOrNull { it.partNumber == partNumber }
            }
            val resolvedPart = partInfo ?: throw XueHuaException.from(
                SdkErrorCode.FILE_UPLOAD_FAILED,
                "missing sign for part $partNumber",
            )
            val putUrl = buildPartPutUrl(sign, resolvedPart)
            if (putUrl.isBlank()) {
                throw XueHuaException.from(
                    SdkErrorCode.FILE_UPLOAD_FAILED,
                    "missing put url for part $partNumber",
                )
            }
            val headers = buildPartHeaders(sign.header, resolvedPart.header)
            httpClient.putBytes(putUrl, partBytes, headers)

            val localMd5 = fileSystem.md5(partBytes)
            if (localMd5 != partMd5s[index]) {
                throw XueHuaException.from(SdkErrorCode.FILE_UPLOAD_FAILED, "part md5 mismatch")
            }

            partEtags[index] = partMd5s[index]
            uploadedSize += currentPartSize
            val uploadedParts = buildList {
                for (idx in 0 until partNum) {
                    if (partEtags[idx] != null) add(idx + 1)
                }
            }
            uploadRecord = uploadRecord.copy(
                uploadedParts = UploadPartsCodec.encode(uploadedParts),
                updateTime = System.currentTimeMillis(),
            )
            databaseService.upsertUploadTask(uploadRecord)

            val progress = ((uploadedSize * 100) / fileSize).toInt().coerceIn(0, 100)
            onProgress?.invoke(progress)
            emitProgress(progressId, clientMsgId, progress, fileSize, uploadedSize)
        }

        val completeResp = apiService.completeMultipartUpload(
            CompleteMultipartUploadReq(
                uploadID = uploadID,
                parts = partMd5s,
                name = objectName,
                contentType = contentType,
            ),
        )
        val resultUrl = completeResp.url
        if (resultUrl.isBlank()) {
            throw XueHuaException.from(SdkErrorCode.FILE_UPLOAD_FAILED, "empty upload url")
        }
        databaseService.deleteUpload(uploadID)
        onProgress?.invoke(100)
        emitProgress(progressId, clientMsgId, 100, fileSize, fileSize)
        return UploadFileResult(url = resultUrl, uuid = hash)
    }

    private fun readPartBytes(source: UploadSource, offset: Long, length: Int): ByteArray =
        when (source) {
            is UploadSource.Bytes -> {
                val start = offset.toInt()
                val end = (start + length).coerceAtMost(source.data.size)
                source.data.copyOfRange(start, end)
            }
            is UploadSource.Path -> fileSystem.readBytes(source.path, offset, length)
        }

    private suspend fun emitProgress(
        uploadId: String,
        clientMsgId: String?,
        progress: Int,
        total: Long,
        current: Long,
    ) {
        eventEmitter.emitUploadProgress(
            UploadProgressEvent(uploadId = uploadId, progress = progress, total = total, current = current),
        )
        clientMsgId?.let {
            eventEmitter.emitMessage(MessageEvent.SendProgress(clientMsgId = it, progress = progress))
        }
    }

    private suspend fun resolvePartSize(fileSize: Long): Long {
        if (partLimitCache == null) {
            runCatching { partLimitCache = apiService.getPartLimit() }
        }
        val limit = partLimitCache ?: return DEFAULT_PART_SIZE
        val minPartSize = limit.minPartSize
        val maxPartSize = limit.maxPartSize
        val maxNumSize = limit.maxNumSize
        if (fileSize > maxPartSize * maxNumSize) {
            throw XueHuaException.from(SdkErrorCode.FILE_UPLOAD_FAILED, "file too large")
        }
        if (fileSize <= minPartSize * maxNumSize) return minPartSize
        var partSize = fileSize / maxNumSize
        if (fileSize % maxNumSize != 0L) partSize++
        return partSize
    }

    private fun buildObjectName(userId: String, fileName: String): String {
        val prefix = "$userId/"
        var objectName = fileName
        if (!objectName.startsWith(prefix)) objectName = "$prefix$objectName"
        if (objectName.startsWith("/")) objectName = objectName.removePrefix("/")
        return objectName
    }

    private fun buildPartPutUrl(sign: UploadSignInfo, partInfo: UploadPartSign): String {
        if (partInfo.url.isNotEmpty()) return partInfo.url
        if (sign.url.isBlank()) return ""
        val uploadIdParam = sign.query.firstOrNull { it.key == "uploadId" }?.values?.firstOrNull()
        val questionIdx = sign.url.indexOf('?')
        val base = if (questionIdx >= 0) sign.url.substring(0, questionIdx) else sign.url
        val queryParts = mutableListOf<String>()
        if (questionIdx >= 0) {
            sign.url.substring(questionIdx + 1)
                .split('&')
                .filter { it.isNotBlank() }
                .forEach { queryParts.add(it) }
        }
        uploadIdParam?.let {
            queryParts.removeAll { part -> part.startsWith("uploadId=") }
            queryParts.add("uploadId=${urlEncode(it)}")
        }
        partInfo.query.forEach { item ->
            val value = item.values.firstOrNull() ?: return@forEach
            if (item.key.isNotBlank()) {
                queryParts.removeAll { part -> part.startsWith("${item.key}=") }
                queryParts.add("${urlEncode(item.key)}=${urlEncode(value)}")
            }
        }
        return if (queryParts.isEmpty()) base else "$base?${queryParts.joinToString("&")}"
    }

    private fun urlEncode(value: String): String = value
        .replace("%", "%25")
        .replace(" ", "%20")
        .replace("&", "%26")
        .replace("=", "%3D")
        .replace("+", "%2B")

    private fun buildPartHeaders(
        signHeaders: List<UploadKeyValue>,
        partHeaders: List<UploadKeyValue>,
    ): Map<String, String> {
        val headers = linkedMapOf<String, String>()
        signHeaders.forEach { item ->
            val value = item.values.firstOrNull()
            if (item.key.isNotBlank() && value != null) headers[item.key] = value
        }
        partHeaders.forEach { item ->
            val value = item.values.firstOrNull()
            if (item.key.isNotBlank() && value != null) headers[item.key] = value
        }
        return headers
    }

    private fun guessContentType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return MIME_TYPES[ext] ?: "application/octet-stream"
    }

    private sealed class UploadSource {
        data class Bytes(val data: ByteArray) : UploadSource()
        data class Path(val path: String) : UploadSource()
    }

    private companion object {
        const val DEFAULT_PART_SIZE = 5L * 1024 * 1024
        val MIME_TYPES = mapOf(
            "png" to "image/png",
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "gif" to "image/gif",
            "bmp" to "image/bmp",
            "webp" to "image/webp",
            "mp4" to "video/mp4",
            "mov" to "video/quicktime",
            "mp3" to "audio/mpeg",
            "wav" to "audio/wav",
            "pdf" to "application/pdf",
            "txt" to "text/plain",
            "json" to "application/json",
            "zip" to "application/zip",
        )
    }
}
