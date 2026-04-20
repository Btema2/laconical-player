package com.laconical.player.core.data

import com.laconical.player.core.data.db.entity.PlayHistory
import com.laconical.player.core.data.db.entity.Playlist
import com.laconical.player.core.data.db.entity.PlaylistTrack
import kotlinx.coroutines.flow.Flow

interface UserDataRepository {
    // Favorites
    fun getAllFavoriteIds(): Flow<List<Long>>
    fun isFavorite(trackId: Long): Flow<Boolean>
    suspend fun addFavorite(trackId: Long)
    suspend fun removeFavorite(trackId: Long)

    // Playlists
    fun getAllPlaylists(): Flow<List<Playlist>>
    suspend fun createPlaylist(name: String): Long
    suspend fun renamePlaylist(playlistId: Long, newName: String)
    suspend fun deletePlaylist(playlistId: Long)
    fun getTrackIdsForPlaylist(playlistId: Long): Flow<List<Long>>
    suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long, position: Int)
    suspend fun appendTrackToPlaylist(playlistId: Long, trackId: Long)
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long)
    suspend fun reorderPlaylistTracks(playlistId: Long, tracks: List<PlaylistTrack>)
    fun getAllPlaylistTracks(): Flow<List<PlaylistTrack>>

    // History
    fun getRecentHistory(limit: Int = 50): Flow<List<PlayHistory>>
    suspend fun recordPlay(trackId: Long)
    suspend fun getPlayCount(trackId: Long): Int
    suspend fun clearHistory()

    // Maintenance
    suspend fun purgeStaleTrackIds(liveTrackIds: Set<Long>)
}
