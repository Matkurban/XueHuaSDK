package com.kurban.xuehuaim.sdk.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver

internal fun ensureOpenIMSchema(driver: SqlDriver) {
    val tableExists = driver.executeQuery(
        identifier = null,
        sql = "SELECT 1 FROM sqlite_master WHERE type='table' AND name='local_users' LIMIT 1",
        mapper = { cursor ->
            when (val next = cursor.next()) {
                is QueryResult.Value -> QueryResult.Value(next.value)
                else -> QueryResult.Value(false)
            }
        },
        parameters = 0,
    ).value == true
    if (!tableExists) {
        OpenIMDatabase.Schema.create(driver)
    }
}
