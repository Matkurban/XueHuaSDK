package com.kurban.xuehuaim.sdk.db

data class UploadRecord(
    val uploadID: String,
    val hash: String? = null,
    val name: String? = null,
    val fileSize: Long? = null,
    val partSize: Long? = null,
    val partNum: Int? = null,
    val uploadedParts: String? = null,
    val updateTime: Long? = null,
)
