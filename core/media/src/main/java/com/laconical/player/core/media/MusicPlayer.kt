package com.laconical.player.core.media

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
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

data class QueueSnapshot(val mediaIds: List<Long>, val index: Int) {
    val isEmpty: Boolean get() = mediaIds.isEmpty()
}

interface MusicPlayer {
    val isPlaying: StateFlow<Boolean>
    val currentPosition: StateFlow<Long>
    val duration: StateFlow<Long>
    val currentMediaItemIndex: StateFlow<Int>
    val shuffleModeEnabled: StateFlow<Boolean>
    val repeatMode: StateFlow<Int>

    /** Suspends until the MediaController connection is established (or fails gracefully). */
    suspend fun awaitConnection()

    /** Snapshot of the live controller queue — mediaIds + current index. Empty if no queue loaded. */
    fun currentQueueSnapshot(): QueueSnapshot

    fun play()
    fun pause()
    fun stop()
    fun skipToPrevious()
    fun skipToNext()
    fun seekTo(position: Long)

    /** Load an entire playlist and start playing from [startIndex]. */
    fun setPlaylist(items: List<MediaItem>, startIndex: Int)

    /** Load an entire playlist paused at [startIndex] (no play()). Used for session restore. */
    fun setPlaylistPaused(items: List<MediaItem>, startIndex: Int)

    fun setShuffle(enabled: Boolean)
    fun setRepeatMode(mode: Int)

    fun toggleShuffle()
    fun cycleRepeatMode()

    /** Seek to a specific index in the current queue and start playing. */
    fun seekToQueueIndex(index: Int)

    /** Move a media item in the queue from [from] to [to]. ExoPlayer adjusts currentMediaItemIndex automatically. */
    fun moveQueueItem(from: Int, to: Int)

    /** Release the MediaController IPC connection and cancel internal coroutines. */
    fun release()
}

@Singleton
class MusicPlayerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : MusicPlayer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaController: MediaController? = null
    private val connectionDeferred = CompletableDeferred<Unit>()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    override val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    override val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _currentMediaItemIndex = MutableStateFlow(0)
    override val currentMediaItemIndex: StateFlow<Int> = _currentMediaItemIndex.asStateFlow()

    private val _shuffleModeEnabled = MutableStateFlow(false)
    override val shuffleModeEnabled: StateFlow<Boolean> = _shuffleModeEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    override val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    init {
        scope.launch {
            try {
                val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
                val controller = MediaController.Builder(context, sessionToken).buildAsync().await()
                mediaController = controller
                controller.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        _isPlaying.value = playing
                        if (playing) startPollingProgress()
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            _duration.value = controller.duration
                        }
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        _currentMediaItemIndex.value = controller.currentMediaItemIndex
                        _duration.value = controller.duration
                    }

                    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                        _currentMediaItemIndex.value = controller.currentMediaItemIndex
                    }

                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                        _shuffleModeEnabled.value = shuffleModeEnabled
                    }

                    override fun onRepeatModeChanged(repeatMode: Int) {
                        _repeatMode.value = repeatMode
                    }
                })
                // Seed StateFlows from controller — listeners don't fire retroactively on reconnect.
                _currentMediaItemIndex.value = controller.currentMediaItemIndex
                _isPlaying.value = controller.isPlaying
                _duration.value = controller.duration
                _shuffleModeEnabled.value = controller.shuffleModeEnabled
                _repeatMode.value = controller.repeatMode
                if (controller.isPlaying) startPollingProgress()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                connectionDeferred.complete(Unit)
            }
        }
    }

    private var pollingJob: kotlinx.coroutines.Job? = null

    private fun startPollingProgress() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (_isPlaying.value) {
                _currentPosition.value = mediaController?.currentPosition ?: 0L
                kotlinx.coroutines.delay(50)
            }
        }
    }

    override suspend fun awaitConnection() {
        connectionDeferred.await()
    }

    override fun currentQueueSnapshot(): QueueSnapshot {
        val mc = mediaController ?: return QueueSnapshot(emptyList(), 0)
        if (mc.mediaItemCount == 0) return QueueSnapshot(emptyList(), 0)
        val ids = (0 until mc.mediaItemCount).mapNotNull { mc.getMediaItemAt(it).mediaId.toLongOrNull() }
        return QueueSnapshot(ids, mc.currentMediaItemIndex)
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

    override fun setPlaylist(items: List<MediaItem>, startIndex: Int) {
        try {
            mediaController?.setMediaItems(items, startIndex, 0L)
            mediaController?.prepare()
            mediaController?.play()
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun setPlaylistPaused(items: List<MediaItem>, startIndex: Int) {
        try {
            mediaController?.setMediaItems(items, startIndex, 0L)
            mediaController?.prepare()
            // Optimistic update — onTimelineChanged fires async; seeding now prevents
            // the persistence combiner's first emit from writing index=0.
            _currentMediaItemIndex.value = startIndex
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun setShuffle(enabled: Boolean) {
        try {
            mediaController?.shuffleModeEnabled = enabled
            _shuffleModeEnabled.value = enabled
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun setRepeatMode(mode: Int) {
        try {
            mediaController?.repeatMode = mode
            _repeatMode.value = mode
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun toggleShuffle() {
        try {
            val mc = mediaController ?: return
            mc.shuffleModeEnabled = !mc.shuffleModeEnabled
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun cycleRepeatMode() {
        try {
            val mc = mediaController ?: return
            mc.repeatMode = when (mc.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun seekToQueueIndex(index: Int) {
        try { mediaController?.seekTo(index, 0L); mediaController?.play() } catch (e: Exception) { e.printStackTrace() }
    }

    override fun moveQueueItem(from: Int, to: Int) {
        try { mediaController?.moveMediaItem(from, to) } catch (e: Exception) { e.printStackTrace() }
    }

    override fun release() {
        pollingJob?.cancel()
        mediaController?.release()
        mediaController = null
        scope.cancel()
    }
}
