package com.laconical.player.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.laconical.player.core.data.db.entity.FavoriteTrack
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addFavorite(track: FavoriteTrack)

    @Query("DELETE FROM favorite_tracks WHERE trackId = :trackId")
    suspend fun removeFavorite(trackId: Long)

    @Query("SELECT trackId FROM favorite_tracks")
    fun getAllFavoriteIds(): Flow<List<Long>>

    @Query("SELECT COUNT(*) > 0 FROM favorite_tracks WHERE trackId = :trackId")
    fun isFavorite(trackId: Long): Flow<Boolean>
}
