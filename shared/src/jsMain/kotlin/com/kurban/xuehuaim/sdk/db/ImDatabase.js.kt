package com.kurban.xuehuaim.sdk.db

import com.kurban.xuehuaim.sdk.platform.DatabaseDriverFactory

internal actual fun createImDatabase(
    driverFactory: DatabaseDriverFactory,
    dbPath: String
): ImDatabase =
    InMemoryImDatabase()
