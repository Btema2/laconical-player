package com.laconical.player.core.media

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interface representing the music player functionality in the Clean Architecture.
 * It provides a way to interact with the playback engine from the UI layer.
 */
interface MusicPlayer {
    /**
     * Observable state indicating whether the player is currently playing.
     */
    val isPlaying: kotlinx.coroutines.flow.StateFlow<Boolean>

    /**
     * Starts or resumes playback.
     */
    fun play()

    /**
     * Pauses playback.
     */
    fun pause()

    /**
     * Stops playback.
     */
    fun stop()

    /**
     * Plays a specific media item.
     * @param mediaItem The media item to play.
     */
    fun playMediaItem(mediaItem: androidx.media3.common.MediaItem)
}

@Singleton
class MusicPlayerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : MusicPlayer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaController: MediaController? = null

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    init {
        scope.launch {
            val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
            mediaController = controllerFuture.await().apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                    }
                })
            }
        }
    }

    override fun play() {
        try {
            mediaController?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun pause() {
        try {
            mediaController?.pause()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun stop() {
        try {
            mediaController?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun playMediaItem(mediaItem: androidx.media3.common.MediaItem) {
        try {
            mediaController?.setMediaItem(mediaItem)
            mediaController?.prepare()
            mediaController?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
