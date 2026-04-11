package com.laconical.player.core.media.di

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
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
    @androidx.annotation.OptIn(UnstableApi::class)
    fun providePlayer(
        @ApplicationContext context: Context,
        audioAttributes: AudioAttributes
    ): ExoPlayer {
        return ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true) // true = handleAudioFocus
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    // MediaSession is intentionally NOT provided here. It is created in
    // PlaybackService.onCreate() and released in PlaybackService.onDestroy() so
    // it follows the service lifecycle rather than the app singleton lifecycle.
    // This prevents the crash where a released singleton MediaSession is reused
    // after the service is destroyed and restarted.

    // Not @Singleton — each ViewModel gets its own MediaController client.
    // The MediaController is released in MainViewModel.onCleared() via MusicPlayer.release().
    @Provides
    fun provideMusicPlayer(
        @ApplicationContext context: Context
    ): MusicPlayer = MusicPlayerImpl(context)

    @Provides
    @Singleton
    fun provideAudioVisualizerManager(
        player: ExoPlayer
    ): com.laconical.player.core.media.AudioVisualizerManager =
        com.laconical.player.core.media.AudioVisualizerManager(player)
}
