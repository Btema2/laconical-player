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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

interface MusicPlayer {
    val isPlaying: kotlinx.coroutines.flow.StateFlow<Boolean>
    val currentPosition: kotlinx.coroutines.flow.StateFlow<Long>
    val duration: kotlinx.coroutines.flow.StateFlow<Long>

    fun play()
    fun pause()
    fun stop()
    fun skipToPrevious()
    fun skipToNext()
    fun seekTo(position: Long)
    fun playMediaItem(mediaItem: androidx.media3.common.MediaItem)

    /** Release the MediaController IPC connection and cancel internal coroutines. */
    fun release()
}

@Singleton
class MusicPlayerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : MusicPlayer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaController: MediaController? = null

        private val _isPlaying = MutableStateFlow(false)
        override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

        private val _currentPosition = MutableStateFlow(0L)
        override val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

        private val _duration = MutableStateFlow(0L)
        override val duration: StateFlow<Long> = _duration.asStateFlow()

        init {
            scope.launch {
                val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
                val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
                mediaController = controllerFuture.await().apply {
                    addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            _isPlaying.value = isPlaying
                            if (isPlaying) {
                                startPollingProgress()
                            }
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_READY) {
                                _duration.value = duration
                            }
                        }
                    })
                }
            }
        }

        private var pollingJob: kotlinx.coroutines.Job? = null

            private fun startPollingProgress() {
                pollingJob?.cancel()
                pollingJob = scope.launch {
                    while (_isPlaying.value) {
                        _currentPosition.value = mediaController?.currentPosition ?: 0L
                        kotlinx.coroutines.delay(50)   // was 1000 — 20 Hz for smooth amplitude tracking
                    }
                }
            }

            override fun play() {
                try { mediaController?.play() } catch (e: Exception) { e.printStackTrace() }
            }

            override fun pause() {
                try { mediaController?.pause() } catch (e: Exception) { e.printStackTrace() }
            }

            override fun stop() {
                try { mediaController?.stop() } catch (e: Exception) { e.printStackTrace() }
            }

            override fun skipToPrevious() {
                try { mediaController?.seekToPrevious() } catch (e: Exception) { e.printStackTrace() }
            }

            override fun skipToNext() {
                try { mediaController?.seekToNext() } catch (e: Exception) { e.printStackTrace() }
            }

            override fun seekTo(position: Long) {
                try { mediaController?.seekTo(position) } catch (e: Exception) { e.printStackTrace() }
            }

            override fun playMediaItem(mediaItem: androidx.media3.common.MediaItem) {
                try {
                    mediaController?.setMediaItem(mediaItem)
                    mediaController?.prepare()
                    mediaController?.play()
                } catch (e: Exception) { e.printStackTrace() }
            }

            override fun release() {
                pollingJob?.cancel()
                mediaController?.release()
                mediaController = null
                scope.cancel()
            }
}
