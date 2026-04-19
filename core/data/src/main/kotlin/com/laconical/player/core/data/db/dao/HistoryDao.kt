package com.laconical.player.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.laconical.player.core.data.db.entity.PlayHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun recordPlay(history: PlayHistory)

    @Query("SELECT * FROM play_history ORDER BY playedAt DESC LIMIT :limit")
    fun getRecentHistory(limit: Int): Flow<List<PlayHistory>>

    @Query("SELECT COUNT(*) FROM play_history WHERE trackId = :trackId")
    suspend fun getPlayCount(trackId: Long): Int

    @Query("DELETE FROM play_history")
    suspend fun clearHistory()

    @Query("DELETE FROM play_history WHERE trackId NOT IN (:liveIds)")
    suspend fun deleteStaleTrackIds(liveIds: Set<Long>)
}
