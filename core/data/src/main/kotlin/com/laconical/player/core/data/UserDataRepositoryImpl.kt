package com.laconical.player.core.data

import com.laconical.player.core.data.db.dao.FavoriteDao
import com.laconical.player.core.data.db.dao.HistoryDao
import com.laconical.player.core.data.db.dao.PlaylistDao
import com.laconical.player.core.data.db.entity.FavoriteTrack
import com.laconical.player.core.data.db.entity.PlayHistory
import com.laconical.player.core.data.db.entity.Playlist
import com.laconical.player.core.data.db.entity.PlaylistTrack
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserDataRepositoryImpl @Inject constructor(
    private val favoriteDao: FavoriteDao,
    private val playlistDao: PlaylistDao,
    private val historyDao: HistoryDao
) : UserDataRepository {

    override fun getAllFavoriteIds() = favoriteDao.getAllFavoriteIds()
    override fun isFavorite(trackId: Long) = favoriteDao.isFavorite(trackId)
    override suspend fun addFavorite(trackId: Long) = favoriteDao.addFavorite(FavoriteTrack(trackId))
    override suspend fun removeFavorite(trackId: Long) = favoriteDao.removeFavorite(trackId)

    override fun getAllPlaylists() = playlistDao.getAllPlaylists()
    override suspend fun createPlaylist(name: String) = playlistDao.createPlaylist(Playlist(name = name))
    override suspend fun renamePlaylist(playlistId: Long, newName: String) =
        playlistDao.renamePlaylist(playlistId, newName)
    override suspend fun deletePlaylist(playlistId: Long) = playlistDao.deletePlaylist(playlistId)
    override fun getTrackIdsForPlaylist(playlistId: Long) = playlistDao.getTrackIdsForPlaylist(playlistId)
    override suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long, position: Int) =
        playlistDao.addTrackToPlaylist(PlaylistTrack(playlistId, trackId, position))
    override suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) =
        playlistDao.removeTrackFromPlaylist(playlistId, trackId)

    override fun getRecentHistory(limit: Int) = historyDao.getRecentHistory(limit)
    override suspend fun recordPlay(trackId: Long) = historyDao.recordPlay(PlayHistory(trackId = trackId))
    override suspend fun getPlayCount(trackId: Long) = historyDao.getPlayCount(trackId)
    override suspend fun clearHistory() = historyDao.clearHistory()
}
