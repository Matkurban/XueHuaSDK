package com.kurban.xuehuaim.sdk.model

import com.kurban.xuehuaim.sdk.enum.ReceiveMessageOpt
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class UserInfoSerializationTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun deserializeUserInfoWithIntGlobalRecvMsgOpt() {
        val payload = """
            {
              "userID": "7976982000",
              "nickname": "test",
              "globalRecvMsgOpt": 0
            }
        """.trimIndent()

        val user = json.decodeFromString<UserInfo>(payload)

        assertEquals("7976982000", user.userID)
        assertEquals(ReceiveMessageOpt.NORMAL, user.globalRecvMsgOpt)
    }
}
