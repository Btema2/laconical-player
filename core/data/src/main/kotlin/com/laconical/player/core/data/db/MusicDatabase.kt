package com.laconical.player.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.laconical.player.core.data.db.dao.FavoriteDao
import com.laconical.player.core.data.db.dao.HistoryDao
import com.laconical.player.core.data.db.dao.LyricsDao
import com.laconical.player.core.data.db.dao.PlaylistDao
import com.laconical.player.core.data.db.entity.FavoriteTrack
import com.laconical.player.core.data.db.entity.LyricsEntity
import com.laconical.player.core.data.db.entity.PlayHistory
import com.laconical.player.core.data.db.entity.Playlist
import com.laconical.player.core.data.db.entity.PlaylistTrack

@Database(
    entities = [FavoriteTrack::class, Playlist::class, PlaylistTrack::class, PlayHistory::class, LyricsEntity::class],
    version = 2,
    exportSchema = true
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun historyDao(): HistoryDao
    abstract fun lyricsDao(): LyricsDao
}
