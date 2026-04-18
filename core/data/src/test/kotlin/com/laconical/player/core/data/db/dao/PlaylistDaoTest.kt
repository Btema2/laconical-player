package com.laconical.player.core.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.laconical.player.core.data.db.MusicDatabase
import com.laconical.player.core.data.db.entity.Playlist
import com.laconical.player.core.data.db.entity.PlaylistTrack
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaylistDaoTest {

    private lateinit var db: MusicDatabase
    private lateinit var dao: PlaylistDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MusicDatabase::class.java
        ).allowMainThreadQueries()
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("PRAGMA foreign_keys = ON")
                }
            })
            .build()
        dao = db.playlistDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `createPlaylist appears in getAllPlaylists`() = runTest {
        dao.createPlaylist(Playlist(name = "Chill Vibes"))
        val playlists = dao.getAllPlaylists().first()
        assertEquals(1, playlists.size)
        assertEquals("Chill Vibes", playlists[0].name)
    }

    @Test
    fun `deletePlaylist removes it from getAllPlaylists`() = runTest {
        val id = dao.createPlaylist(Playlist(name = "Temp"))
        dao.deletePlaylist(id)
        assertTrue(dao.getAllPlaylists().first().isEmpty())
    }

    @Test
    fun `deletePlaylist cascades to playlist_tracks`() = runTest {
        val playlistId = dao.createPlaylist(Playlist(name = "Test"))
        dao.addTrackToPlaylist(PlaylistTrack(playlistId, trackId = 10L, position = 0))
        dao.deletePlaylist(playlistId)
        assertTrue(dao.getTrackIdsForPlaylist(playlistId).first().isEmpty())
    }

    @Test
    fun `addTrackToPlaylist appears in getTrackIdsForPlaylist`() = runTest {
        val playlistId = dao.createPlaylist(Playlist(name = "Test"))
        dao.addTrackToPlaylist(PlaylistTrack(playlistId, trackId = 10L, position = 0))
        val tracks = dao.getTrackIdsForPlaylist(playlistId).first()
        assertEquals(listOf(10L), tracks)
    }

    @Test
    fun `removeTrackFromPlaylist removes only that track`() = runTest {
        val playlistId = dao.createPlaylist(Playlist(name = "Test"))
        dao.addTrackToPlaylist(PlaylistTrack(playlistId, 10L, 0))
        dao.addTrackToPlaylist(PlaylistTrack(playlistId, 20L, 1))
        dao.removeTrackFromPlaylist(playlistId, 10L)
        val tracks = dao.getTrackIdsForPlaylist(playlistId).first()
        assertEquals(listOf(20L), tracks)
    }

    @Test
    fun `tracks returned in position order`() = runTest {
        val playlistId = dao.createPlaylist(Playlist(name = "Ordered"))
        dao.addTrackToPlaylist(PlaylistTrack(playlistId, 30L, 2))
        dao.addTrackToPlaylist(PlaylistTrack(playlistId, 10L, 0))
        dao.addTrackToPlaylist(PlaylistTrack(playlistId, 20L, 1))
        val tracks = dao.getTrackIdsForPlaylist(playlistId).first()
        assertEquals(listOf(10L, 20L, 30L), tracks)
    }
}
