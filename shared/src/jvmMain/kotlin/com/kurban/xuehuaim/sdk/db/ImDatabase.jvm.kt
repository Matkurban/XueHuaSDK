package com.kurban.xuehuaim.sdk.db

import com.kurban.xuehuaim.sdk.platform.DatabaseDriverFactory

internal actual suspend fun createImDatabase(
    driverFactory: DatabaseDriverFactory,
    dbPath: String,
): ImDatabase {
    val driver = driverFactory.createDriver(dbPath)
    driverFactory.initializeSchema(driver)
    val database = OpenIMDatabase(driver)
    return SqlDelightImDatabase(driver, database)
}
