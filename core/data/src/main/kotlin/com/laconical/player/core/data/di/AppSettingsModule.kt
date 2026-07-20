package com.laconical.player.core.data.di

import com.laconical.player.core.data.AppSettingsStore
import com.laconical.player.core.data.DataStoreAppSettingsStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppSettingsModule {

    @Binds
    @Singleton
    abstract fun bindAppSettingsStore(
        impl: DataStoreAppSettingsStore,
    ): AppSettingsStore
}
