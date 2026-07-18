package com.laconical.player.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted lyrics for a track. [syncedLyrics] holds the ORIGINAL raw LRC text — it is
 * re-parsed on read, never round-tripped through the domain model, so nothing is lost.
 */
@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey val trackId: Long,
    val plainLyrics: String?,
    val syncedLyrics: String?,
    val source: String,
    val fetchedAtMs: Long,
    val instrumental: Boolean
)
