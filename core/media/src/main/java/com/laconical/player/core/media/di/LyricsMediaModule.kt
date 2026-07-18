package com.laconical.player.core.media.di

import com.laconical.player.core.data.lyrics.EmbeddedFormatLyricsExtractor
import com.laconical.player.core.media.lyrics.Media3EmbeddedFormatLyricsExtractor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class LyricsMediaModule {

    @Binds
    abstract fun bindEmbeddedFormatLyricsExtractor(
        impl: Media3EmbeddedFormatLyricsExtractor
    ): EmbeddedFormatLyricsExtractor
}
