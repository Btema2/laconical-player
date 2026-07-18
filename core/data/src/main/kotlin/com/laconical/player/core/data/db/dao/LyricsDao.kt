package com.laconical.player.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.laconical.player.core.data.db.entity.LyricsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LyricsDao {

    @Query("SELECT * FROM lyrics WHERE trackId = :trackId")
    suspend fun getByTrackId(trackId: Long): LyricsEntity?

    @Query("SELECT * FROM lyrics WHERE trackId = :trackId")
    fun observeByTrackId(trackId: Long): Flow<LyricsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LyricsEntity)

    @Query("DELETE FROM lyrics WHERE trackId = :trackId")
    suspend fun delete(trackId: Long)

    /** Removes lyrics for tracks that no longer exist in MediaStore. */
    @Query("DELETE FROM lyrics WHERE trackId NOT IN (:liveIds)")
    suspend fun deleteStaleTrackIds(liveIds: Set<Long>)
}
