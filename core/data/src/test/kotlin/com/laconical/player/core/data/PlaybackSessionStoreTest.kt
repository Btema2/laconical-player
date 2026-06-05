package com.laconical.player.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackSessionStoreTest {

    private lateinit var store: DataStorePlaybackSessionStore

    @Before
    fun setUp() = runBlocking {
        store = DataStorePlaybackSessionStore(ApplicationProvider.getApplicationContext<Context>())
        // Always clear so tests are order-independent: the preferencesDataStore delegate
        // is a file-level singleton that can survive between test methods.
        store.clear()
    }

    @Test
    fun `round-trip all fields`() = runTest {
        val session = PlaybackSession(
            trackIds = listOf(1L, 2L, 3L),
            index = 1,
            shuffle = true,
            repeat = 1,
        )
        store.save(session)
        val loaded = store.session.first()
        assertEquals(session, loaded)
    }

    @Test
    fun `clear results in null session`() = runTest {
        store.save(PlaybackSession(trackIds = listOf(7L), index = 0, shuffle = false, repeat = 0))
        store.clear()
        val loaded = store.session.first()
        assertNull(loaded)
    }

    @Test
    fun `no save yields null session`() = runTest {
        // Store was cleared in @Before; no save here
        val loaded = store.session.first()
        assertNull(loaded)
    }

    @Test
    fun `single track round-trips correctly`() = runTest {
        val session = PlaybackSession(trackIds = listOf(42L), index = 0, shuffle = false, repeat = 0)
        store.save(session)
        val loaded = store.session.first()
        assertEquals(session, loaded)
    }

    @Test
    fun `shuffle false and repeat 0 are not coerced to true or 1`() = runTest {
        val session = PlaybackSession(trackIds = listOf(5L, 6L), index = 0, shuffle = false, repeat = 0)
        store.save(session)
        val loaded = store.session.first()!!
        assertFalse("shuffle should be false", loaded.shuffle)
        assertEquals("repeat should be 0", 0, loaded.repeat)
    }
}
