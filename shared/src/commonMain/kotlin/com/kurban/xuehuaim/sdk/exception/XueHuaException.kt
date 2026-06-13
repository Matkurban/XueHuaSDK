package com.kurban.xuehuaim.sdk.exception

import com.kurban.xuehuaim.sdk.enum.SdkErrorCode

class XueHuaException(
    val code: Int,
    override val message: String,
) : Exception(message) {
    val sdkErrorCode: SdkErrorCode? get() = SdkErrorCode.fromCode(code)

    companion object {
        fun from(errorCode: SdkErrorCode, detail: String? = null): XueHuaException =
            XueHuaException(errorCode.code, detail ?: errorCode.message)
    }

    override fun toString(): String = "XueHuaException(code=$code, message=$message)"
}
