package com.laconical.player.core.data.db.entity

import androidx.room.Entity

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"]
)
data class PlaylistTrack(
    val playlistId: Long,
    val trackId: Long,
    val position: Int
)
