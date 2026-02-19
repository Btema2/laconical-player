package com.laconical.player.core.media.di

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import com.laconical.player.core.media.MusicPlayer
import com.laconical.player.core.media.MusicPlayerImpl

@Module
@InstallIn(SingletonComponent::class)
object MediaModule {

    @Provides
    @Singleton
    fun provideAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    @Provides
    @Singleton
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun providePlayer(
        @ApplicationContext context: Context,
        audioAttributes: androidx.media3.common.AudioAttributes
    ): androidx.media3.exoplayer.ExoPlayer {
        return androidx.media3.exoplayer.ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true) // true = handleAudioFocus
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideMediaSession(
        @ApplicationContext context: Context,
        player: ExoPlayer
    ): MediaSession = MediaSession.Builder(context, player)
        .build()

    @Provides
    @Singleton
    fun provideMusicPlayer(
        @ApplicationContext context: Context
    ): MusicPlayer = MusicPlayerImpl(context)
}
