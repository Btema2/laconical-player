package com.laconical.player.ui.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavTransitionsTest {

    @Test
    fun `tracks to albums is forward`() {
        assertTrue(isForwardNavigation("tracks", "albums"))
    }

    @Test
    fun `albums to tracks is backward`() {
        assertFalse(isForwardNavigation("albums", "tracks"))
    }

    @Test
    fun `tracks to artists skips over albums and is forward`() {
        assertTrue(isForwardNavigation("tracks", "artists"))
    }

    @Test
    fun `artists to albums is backward`() {
        assertFalse(isForwardNavigation("artists", "albums"))
    }

    @Test
    fun `playlists to tracks is backward`() {
        assertFalse(isForwardNavigation("playlists", "tracks"))
    }

    @Test
    fun `detail route is always forward`() {
        assertTrue(isForwardNavigation("tracks", "album_detail/Radiohead"))
    }

    @Test
    fun `null routes default to forward`() {
        assertTrue(isForwardNavigation(null, null))
    }
}
