package com.laconical.player.core.model

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val mediaUri: String,
    val albumArtUri: String? = null,
    val dataPath: String? = null,
    /** MediaStore.Audio.Media.TRACK: disc*1000 + track when tagged (e.g. 1004 = disc 1, track 4),
     *  or just the track number with no disc prefix. 0 when untagged. */
    val trackNumber: Int = 0
)
