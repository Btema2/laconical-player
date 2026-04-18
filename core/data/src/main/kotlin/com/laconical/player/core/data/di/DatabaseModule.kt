package com.laconical.player.core.data.di

import android.content.Context
import androidx.room.Room
import com.laconical.player.core.data.db.MusicDatabase
import com.laconical.player.core.data.db.dao.FavoriteDao
import com.laconical.player.core.data.db.dao.HistoryDao
import com.laconical.player.core.data.db.dao.PlaylistDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideMusicDatabase(@ApplicationContext context: Context): MusicDatabase =
        Room.databaseBuilder(context, MusicDatabase::class.java, "music_database.db").build()

    @Provides
    fun provideFavoriteDao(db: MusicDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    fun providePlaylistDao(db: MusicDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun provideHistoryDao(db: MusicDatabase): HistoryDao = db.historyDao()
}
