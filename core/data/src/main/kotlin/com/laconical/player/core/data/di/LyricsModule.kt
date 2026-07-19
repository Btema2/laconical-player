package com.laconical.player.core.data.di

import com.laconical.player.core.data.lyrics.DataStoreLyricsSettingsStore
import com.laconical.player.core.data.lyrics.LyricsRepository
import com.laconical.player.core.data.lyrics.LyricsRepositoryImpl
import com.laconical.player.core.data.lyrics.LyricsSettingsStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LyricsModule {

    @Binds
    @Singleton
    abstract fun bindLyricsRepository(impl: LyricsRepositoryImpl): LyricsRepository

    @Binds
    @Singleton
    abstract fun bindLyricsSettingsStore(impl: DataStoreLyricsSettingsStore): LyricsSettingsStore
}
