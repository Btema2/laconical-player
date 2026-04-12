package com.laconical.player.core.media

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlaylistRepeatTest {

    private lateinit var player: ExoPlayer

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        player = ExoPlayer.Builder(context).build()
    }

    @After
    fun tearDown() {
        player.release()
    }

    /**
     * Verifies that calling [Player.seekToNext] on the last track with
     * [Player.REPEAT_MODE_ALL] wraps back to the first track (index 0).
     */
    @Test
    fun nextOnLastTrackWithRepeatAll_wrapsToFirst() {
        val items = listOf(
            MediaItem.fromUri("content://media/track/1"),
            MediaItem.fromUri("content://media/track/2"),
            MediaItem.fromUri("content://media/track/3"),
        )
        player.setMediaItems(items)
        player.repeatMode = Player.REPEAT_MODE_ALL

        // Seek to the last item directly (no prepare needed for index assertions).
        player.seekTo(items.size - 1, C.TIME_UNSET)

        assertEquals("should be on last track before next", items.size - 1, player.currentMediaItemIndex)

        player.seekToNext()

        assertEquals("REPEAT_MODE_ALL should wrap next to index 0", 0, player.currentMediaItemIndex)
    }

    /**
     * Verifies that [Player.REPEAT_MODE_OFF] does NOT wrap — next on the last
     * track keeps the index at the end.
     */
    @Test
    fun nextOnLastTrackWithRepeatOff_doesNotWrap() {
        val items = listOf(
            MediaItem.fromUri("content://media/track/1"),
            MediaItem.fromUri("content://media/track/2"),
        )
        player.setMediaItems(items)
        player.repeatMode = Player.REPEAT_MODE_OFF

        player.seekTo(items.size - 1, C.TIME_UNSET)
        player.seekToNext()

        assertEquals("REPEAT_MODE_OFF should not wrap — stay on last index", items.size - 1, player.currentMediaItemIndex)
    }
}
