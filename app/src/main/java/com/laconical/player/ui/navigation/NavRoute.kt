package com.laconical.player.ui.navigation

import android.net.Uri

object NavRoute {
    const val TRACKS = "tracks"
    const val ALBUMS = "albums"
    const val ALBUM_DETAIL = "album_detail/{albumName}"
    const val ARTISTS = "artists"
    const val ARTIST_DETAIL = "artist_detail/{artistName}"
    const val PLAYLISTS = "playlists"
    const val FAVORITES = "favorites"

    fun albumDetailRoute(albumName: String): String = "album_detail/${Uri.encode(albumName)}"
    fun artistDetailRoute(artistName: String): String = "artist_detail/${Uri.encode(artistName)}"
}
