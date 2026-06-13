package com.kurban.xuehuaim.sdk.enum

enum class SdkErrorCode(val code: Int, val message: String) {
    UNKNOWN(-1, "unknown error"),
    NETWORK_ERROR(10000, "network error"),
    NOT_INIT(10001, "sdk not initialized"),
    NOT_LOGIN(10002, "not logged in"),
    ALREADY_LOGIN(10003, "already logged in"),
    PARAM_ERROR(10004, "parameter error"),
    TOKEN_EXPIRED(10005, "token expired"),
    KICKED_OFFLINE(10006, "kicked offline"),
    WS_CONNECT_FAILED(10007, "websocket connect failed"),
    DB_ERROR(10008, "database error"),
    MSG_SEND_FAILED(10009, "message send failed"),
    FILE_UPLOAD_FAILED(10010, "file upload failed"),
    SERVER_ERROR(10011, "server error");

    companion object {
        fun fromCode(code: Int): SdkErrorCode? = entries.find { it.code == code }
    }
}
