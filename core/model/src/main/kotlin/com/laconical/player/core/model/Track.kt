package com.laconical.player.core.model

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val mediaUri: String,
    val albumArtUri: String? = null
)
