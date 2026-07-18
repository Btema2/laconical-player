package com.laconical.player.core.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MusicDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MusicDatabase::class.java
    )

    @Test
    fun `migrate 1 to 2 creates lyrics table and preserves data`() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO favorite_tracks (trackId) VALUES (7)")
            execSQL("INSERT INTO playlists (id, name, createdAt) VALUES (1, 'Mix', 123)")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        // Old data survives.
        db.query("SELECT trackId FROM favorite_tracks").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(7L, cursor.getLong(0))
        }
        db.query("SELECT name FROM playlists").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Mix", cursor.getString(0))
        }

        // New table accepts rows.
        db.execSQL(
            "INSERT INTO lyrics (trackId, plainLyrics, syncedLyrics, source, fetchedAtMs, instrumental) " +
                "VALUES (42, 'plain', null, 'LRCLIB', 999, 0)"
        )
        db.query("SELECT plainLyrics FROM lyrics WHERE trackId = 42").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("plain", cursor.getString(0))
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
