package com.kurban.xuehuaim.sdk.model

import com.kurban.xuehuaim.sdk.network.http.PointsTransactionsResp
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PointsTransactionSerializationTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun deserializePointsTransactionWithIso8601CreateTime() {
        val payload = """
            {
              "txID": "tx-1",
              "userID": "user-1",
              "amount": 10.5,
              "txType": 3,
              "relatedID": "rp-1",
              "remark": "红包过期退回",
              "createTime": "2026-05-06T13:22:12.336Z"
            }
        """.trimIndent()

        val tx = json.decodeFromString<PointsTransaction>(payload)

        assertEquals("tx-1", tx.txID)
        assertEquals("红包过期退回", tx.remark)
        assertTrue(tx.createTime > 0)
    }

    @Test
    fun deserializePointsTransactionWithEpochMillisCreateTime() {
        val payload = """
            {
              "txID": "tx-2",
              "userID": "user-1",
              "amount": 5.0,
              "txType": 2,
              "relatedID": "rp-2",
              "remark": "收红包",
              "createTime": 1700000000000
            }
        """.trimIndent()

        val tx = json.decodeFromString<PointsTransaction>(payload)

        assertEquals(1_700_000_000_000, tx.createTime)
    }

    @Test
    fun deserializePointsTransactionsResponseEnvelope() {
        val payload = """
            {
              "errCode": 0,
              "errMsg": "",
              "data": {
                "total": 1,
                "transactions": [
                  {
                    "txID": "tx-1",
                    "userID": "user-1",
                    "amount": 10.5,
                    "txType": 3,
                    "relatedID": "rp-1",
                    "remark": "红包过期退回",
                    "createTime": "2026-05-06T13:22:12.336Z"
                  }
                ]
              }
            }
        """.trimIndent()

        val resp = json.decodeFromString<ApiResponse<PointsTransactionsResp>>(payload)

        assertEquals(1, resp.data?.total)
        assertEquals(1, resp.data?.transactions?.size)
        assertTrue(resp.data?.transactions?.first()?.createTime ?: 0 > 0)
    }
}
