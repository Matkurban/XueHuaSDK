package com.kurban.xuehuaim.sdk.flow

import com.kurban.xuehuaim.sdk.enum.ConnectionState
import com.kurban.xuehuaim.sdk.enum.LoginStatus
import com.kurban.xuehuaim.sdk.event.ConnectionEvent
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class SdkEventEmitterTest {
    @Test
    fun updatesStateFlows() = runBlocking {
        val emitter = SdkEventEmitter()
        emitter.setLoginStatus(LoginStatus.LOGGED)
        emitter.setConnectionState(ConnectionState.CONNECTED)
        emitter.emitConnection(ConnectionEvent.TokenExpired)

        assertEquals(LoginStatus.LOGGED, emitter.loginStatus.value)
        assertEquals(ConnectionState.CONNECTED, emitter.connectionState.value)
    }
}
