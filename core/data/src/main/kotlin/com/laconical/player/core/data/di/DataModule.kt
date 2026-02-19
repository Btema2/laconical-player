package com.laconical.player.core.data.di

import com.laconical.player.core.data.LocalMediaRepositoryImpl
import com.laconical.player.core.data.MediaRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindMediaRepository(
        impl: LocalMediaRepositoryImpl
    ): MediaRepository
}
