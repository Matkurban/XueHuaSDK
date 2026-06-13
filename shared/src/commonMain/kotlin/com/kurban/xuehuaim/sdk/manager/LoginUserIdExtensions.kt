package com.kurban.xuehuaim.sdk.manager

import com.kurban.xuehuaim.sdk.enum.SdkErrorCode
import com.kurban.xuehuaim.sdk.exception.XueHuaException
import com.kurban.xuehuaim.sdk.network.http.LoginUserIdProvider

internal fun LoginUserIdProvider.requireUserId(): String =
    this() ?: throw XueHuaException.from(SdkErrorCode.NOT_LOGIN)
