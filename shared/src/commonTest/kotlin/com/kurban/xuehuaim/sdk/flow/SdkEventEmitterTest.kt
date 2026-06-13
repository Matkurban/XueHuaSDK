package com.kurban.xuehuaim.sdk.flow

import com.kurban.xuehuaim.sdk.enum.ConnectionState
import com.kurban.xuehuaim.sdk.enum.LoginStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class SdkEventEmitterTest {
    @Test
    fun updatesStateFlows() {
        val emitter = SdkEventEmitter()
        emitter.setLoginStatus(LoginStatus.LOGGED)
        emitter.setConnectionState(ConnectionState.CONNECTED)

        assertEquals(LoginStatus.LOGGED, emitter.loginStatus.value)
        assertEquals(ConnectionState.CONNECTED, emitter.connectionState.value)
    }
}
