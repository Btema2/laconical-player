package com.laconical.player.ui

import com.laconical.player.core.model.Track

interface SortLabel {
    val label: String
}

enum class SortOrder(override val label: String) : SortLabel {
    DEFAULT("Default"),
    TITLE("Title"),
    ARTIST("Artist"),
    DURATION("Duration")
}

fun List<Track>.applySort(order: SortOrder): List<Track> = when (order) {
    SortOrder.DEFAULT -> this
    SortOrder.TITLE -> sortedBy { it.title.lowercase() }
    SortOrder.ARTIST -> sortedBy { it.artist.lowercase() }
    SortOrder.DURATION -> sortedByDescending { it.durationMs }
}

enum class AlbumSortOrder(override val label: String) : SortLabel {
    NAME("Name"),
    TRACKS("Tracks"),
    ARTIST("Artist")
}

enum class ArtistSortOrder(override val label: String) : SortLabel {
    NAME("Name"),
    TRACKS("Tracks"),
    ALBUMS("Albums")
}

enum class PlaylistSortOrder(override val label: String) : SortLabel {
    RECENT("Recent"),
    NAME("Name"),
    TRACKS("Tracks")
}
