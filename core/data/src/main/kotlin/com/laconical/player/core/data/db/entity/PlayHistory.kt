package com.laconical.player.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "play_history")
data class PlayHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val playedAt: Long = System.currentTimeMillis()
)
