package com.laconical.player.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v1 → v2: adds the `lyrics` table. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `lyrics` (
                `trackId` INTEGER NOT NULL,
                `plainLyrics` TEXT,
                `syncedLyrics` TEXT,
                `source` TEXT NOT NULL,
                `fetchedAtMs` INTEGER NOT NULL,
                `instrumental` INTEGER NOT NULL,
                PRIMARY KEY(`trackId`)
            )
            """.trimIndent()
        )
    }
}
