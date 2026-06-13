package com.kurban.xuehuaim.sdk.network.http

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue

class RegisterReqSerializationTest {

    private val json = Json { encodeDefaults = true }

    @Test
    fun registerReq_matchesFlutterPayloadShape() {
        val payload = RegisterReq(
            deviceID = "device-uuid",
            verifyCode = "123456",
            platform = 5,
            autoLogin = true,
            user = RegisterUserInfo(
                nickname = "test",
                faceURL = "https://example.com/avatar.jpg",
                birth = 0,
                gender = 1,
                email = "test@example.com",
                password = "md5hash",
            ),
        )
        val encoded = json.encodeToString(payload)
        assertTrue(encoded.contains("\"deviceID\""))
        assertTrue(encoded.contains("\"verifyCode\""))
        assertTrue(encoded.contains("\"autoLogin\""))
        assertTrue(encoded.contains("\"user\""))
        assertTrue(encoded.contains("\"nickname\""))
        assertTrue(encoded.contains("\"password\""))
    }
}
