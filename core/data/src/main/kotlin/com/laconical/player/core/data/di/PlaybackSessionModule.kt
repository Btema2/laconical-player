package com.laconical.player.core.data.di

import com.laconical.player.core.data.DataStorePlaybackSessionStore
import com.laconical.player.core.data.PlaybackSessionStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackSessionModule {

    @Binds
    @Singleton
    abstract fun bindPlaybackSessionStore(
        impl: DataStorePlaybackSessionStore,
    ): PlaybackSessionStore
}
