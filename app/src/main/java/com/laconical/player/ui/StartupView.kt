package com.laconical.player.ui

import com.laconical.player.ui.navigation.NavRoute

/**
 * Which bottom-nav tab the app opens on. Persisted via AppSettingsStore (as .name()),
 * read once at NavHost's first composition to set its startDestination — see
 * LibraryScreen.kt's NavHost gating.
 */
enum class StartupView(val route: String, val label: String) {
    TRACKS(NavRoute.TRACKS, "Tracks"),
    ALBUMS(NavRoute.ALBUMS, "Albums"),
    ARTISTS(NavRoute.ARTISTS, "Artists"),
    PLAYLISTS(NavRoute.PLAYLISTS, "Playlists")
}
