package com.laconical.player.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laconical.player.core.data.MediaRepository
import com.laconical.player.core.data.PlaybackSession
import com.laconical.player.core.data.PlaybackSessionStore
import com.laconical.player.core.data.resolveSession
import com.laconical.player.core.data.UserDataRepository
import com.laconical.player.core.data.db.entity.Playlist
import com.laconical.player.core.media.MusicPlayer
import com.laconical.player.core.media.AudioVisualizerManager
import com.laconical.player.core.model.Track
import com.laconical.player.core.media.WaveformExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.media3.common.MediaItem
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import coil3.asImage
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Wrapper type so Coil dispatches to OUR fetcher, not its built-in ContentUriFetcher. */
data class AudioArtData(
    val uri: String,
    /** MediaStore albumart URI — `content://media/external/audio/albumart/<albumId>`.
     *  When present, all tracks in the same album share this key, collapsing 20 cache
     *  misses into 1 fetch instead of opening MediaMetadataRetriever per track. */
    val albumArtUri: String? = null,
)

class AudioAlbumArtFetcher(
    private val artData: AudioArtData,
    private val options: Options
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val (reqW, reqH) = targetPixels()

        when (embeddedArtCache[artData.uri]) {
            false -> return fetchAlbumArt(reqW, reqH)  // known no embedded art — skip MMR
            true  -> { /* known has embedded art — fall through to MMR below */ }
            null  -> { /* unknown — probe MMR and record result */ }
        }

        val retriever = MediaMetadataRetriever()
        try {
            if (artData.uri.startsWith("/")) {
                retriever.setDataSource(artData.uri)
            } else {
                retriever.setDataSource(options.context, Uri.parse(artData.uri))
            }
            val picture = retriever.embeddedPicture
            if (picture != null) {
                val bitmap = decodeSampled(picture, reqW, reqH)
                if (bitmap != null) {
                    embeddedArtCache[artData.uri] = true
                    return ImageFetchResult(
                        image = bitmap.asImage(),
                        isSampled = reqW > 0,
                        dataSource = DataSource.DISK
                    )
                }
            }
            embeddedArtCache[artData.uri] = false
        } catch (_: Exception) {
            embeddedArtCache[artData.uri] = false
        } finally {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    retriever.close()
                } else {
                    retriever.release()
                }
            } catch (_: Exception) {}
        }

        return fetchAlbumArt(reqW, reqH)
    }

    private fun fetchAlbumArt(reqW: Int, reqH: Int): FetchResult? {
        artData.albumArtUri?.let { artUri ->
            try {
                val bytes = options.context.contentResolver
                    .openInputStream(Uri.parse(artUri))?.use { it.readBytes() }
                    ?: return null
                val bitmap = decodeSampled(bytes, reqW, reqH)
                if (bitmap != null) {
                    return ImageFetchResult(
                        image = bitmap.asImage(),
                        isSampled = reqW > 0,
                        dataSource = DataSource.DISK
                    )
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun targetPixels(): Pair<Int, Int> {
        val w = (options.size.width as? coil3.size.Dimension.Pixels)?.px ?: 0
        val h = (options.size.height as? coil3.size.Dimension.Pixels)?.px ?: 0
        return w to h
    }

    private fun decodeSampled(bytes: ByteArray, reqW: Int, reqH: Int): android.graphics.Bitmap? {
        if (reqW <= 0 || reqH <= 0) {
            return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val opts = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = calcInSampleSize(bounds.outWidth, bounds.outHeight, reqW, reqH)
        }
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun calcInSampleSize(srcW: Int, srcH: Int, reqW: Int, reqH: Int): Int {
        var sample = 1
        if (srcH > reqH || srcW > reqW) {
            var halfH = srcH / 2
            var halfW = srcW / 2
            while (halfH >= reqH && halfW >= reqW) {
                sample *= 2
                halfH /= 2
                halfW /= 2
            }
        }
        return sample
    }

    class Factory : Fetcher.Factory<AudioArtData> {
        override fun create(data: AudioArtData, options: Options, imageLoader: ImageLoader): Fetcher {
            return AudioAlbumArtFetcher(data, options)
        }
    }

    companion object {
        // Tracks whether a URI has embedded art. Populated on first MMR probe.
        // false = skip MMR next time, go straight to albumArtUri fallback.
        val embeddedArtCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    }
}

class AudioAlbumArtKeyer : Keyer<AudioArtData> {
    override fun key(data: AudioArtData, options: Options): String {
        // Key per track URI + size bucket so small thumbnails and full-player art
        // cache separately. albumArtUri used only in fetcher, not as cache key.
        val w = (options.size.width as? coil3.size.Dimension.Pixels)?.px ?: 0
        val bucket = when {
            w <= 0   -> "orig"
            w <= 200 -> "sm"
            w <= 600 -> "md"
            else     -> "lg"
        }
        return "audio_art_${data.uri}_$bucket"
    }
}

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MediaRepository,
    private val musicPlayer: MusicPlayer,
    private val visualizerManager: AudioVisualizerManager,
    private val waveformExtractor: WaveformExtractor,
    private val userDataRepository: UserDataRepository,
    private val sessionStore: PlaybackSessionStore
) : ViewModel() {

    private val _allTracks = MutableStateFlow<List<Track>>(emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.DEFAULT)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    fun setSortOrder(order: SortOrder) { _sortOrder.value = order }

    val tracks: StateFlow<List<Track>> = combine(_allTracks, _searchQuery, _sortOrder) { tracks, query, sort ->
        val filtered = if (query.isBlank()) tracks
        else tracks.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.artist.contains(query, ignoreCase = true)
        }
        when (sort) {
            SortOrder.DEFAULT -> filtered
            SortOrder.TITLE -> filtered.sortedBy { it.title.lowercase() }
            SortOrder.ARTIST -> filtered.sortedBy { it.artist.lowercase() }
            SortOrder.DURATION -> filtered.sortedByDescending { it.durationMs }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val searchedAlbums: StateFlow<List<Track>> = combine(_allTracks, _searchQuery) { allTracks, query ->
        if (query.isBlank()) emptyList()
        else allTracks
            .filter { it.album.contains(query, ignoreCase = true) }
            .distinctBy { it.album.lowercase() }
            .sortedBy { it.album.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val searchedArtists: StateFlow<List<Track>> = combine(_allTracks, _searchQuery) { allTracks, query ->
        if (query.isBlank()) emptyList()
        else allTracks
            .filter { it.artist.contains(query, ignoreCase = true) }
            .distinctBy { it.artist.lowercase() }
            .sortedBy { it.artist.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val searchedPlaylists: StateFlow<List<Playlist>> = combine(
        _searchQuery,
        userDataRepository.getAllPlaylists()
    ) { query, allPlaylists ->
        if (query.isBlank()) emptyList()
        else allPlaylists.filter { it.name.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _playingTrackDominantColor = MutableStateFlow<Color?>(null)
    val playingTrackDominantColor: StateFlow<Color?> = _playingTrackDominantColor.asStateFlow()

    val isPlaying: StateFlow<Boolean> = musicPlayer.isPlaying
    val currentPosition: StateFlow<Long> = musicPlayer.currentPosition
    val duration: StateFlow<Long> = musicPlayer.duration
    val shuffleModeEnabled: StateFlow<Boolean> = musicPlayer.shuffleModeEnabled
    val repeatMode: StateFlow<Int> = musicPlayer.repeatMode
    private val _currentQueue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _currentQueue.asStateFlow()
    val currentQueueIndex: StateFlow<Int> = musicPlayer.currentMediaItemIndex

    val waveform: StateFlow<FloatArray> = visualizerManager.waveform
    val beatPulse: StateFlow<Float> = visualizerManager.beatPulse

    private val _waveformData = MutableStateFlow<List<Int>>(emptyList())
    val waveformData: StateFlow<List<Int>> = _waveformData.asStateFlow()

    val favoriteIds: StateFlow<Set<Long>> = userDataRepository.getAllFavoriteIds()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    fun toggleFavorite(trackId: Long) {
        viewModelScope.launch {
            favoriteMutex.withLock {
                if (favoriteIds.value.contains(trackId)) {
                    userDataRepository.removeFavorite(trackId)
                } else {
                    userDataRepository.addFavorite(trackId)
                }
            }
        }
    }

    val playlists: StateFlow<List<Playlist>> = userDataRepository.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val playlistArtTracks: StateFlow<Map<Long, List<Track>>> = combine(
        userDataRepository.getAllPlaylistTracks(),
        _allTracks
    ) { playlistTracks, allTracks ->
        val trackMap = allTracks.associateBy { it.id }
        playlistTracks
            .groupBy { it.playlistId }
            .mapValues { (_, pts) -> pts.take(4).mapNotNull { trackMap[it.trackId] } }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    fun addTrackToPlaylist(trackId: Long, playlistId: Long) {
        viewModelScope.launch {
            userDataRepository.appendTrackToPlaylist(playlistId, trackId)
        }
    }

    fun createPlaylistAndAdd(name: String, trackId: Long) {
        viewModelScope.launch {
            val playlistId = userDataRepository.createPlaylist(name)
            userDataRepository.appendTrackToPlaylist(playlistId, trackId)
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch { userDataRepository.createPlaylist(name) }
    }

    private val _currentNormalizedAmplitude = MutableStateFlow(0f)
    val currentNormalizedAmplitude: StateFlow<Float> = _currentNormalizedAmplitude.asStateFlow()

    /** Pre-computed peak so we don't scan the list every 16 ms. */
    private var cachedMaxAmplitude = 1

    val progress: StateFlow<Float> = combine(currentPosition, duration) { pos, dur ->
        if (dur > 0) pos.toFloat() / dur.toFloat() else 0f
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0f)

    // Tracked so playTrack() can cancel them when the user switches tracks rapidly.
    private var waveformJob: Job? = null
    private var colorJob: Job? = null
    private val favoriteMutex = kotlinx.coroutines.sync.Mutex()

    init {
        loadTracks()
    }

    fun loadTracks() {
        viewModelScope.launch {
            val loaded = repository.getTracks()
            _allTracks.value = loaded
            val liveIds = loaded.map { it.id }.toSet()
            if (liveIds.isNotEmpty()) {
                userDataRepository.purgeStaleTrackIds(liveIds)
            }
            restorePlaybackSession(loaded)
            startSessionPersistence()
        }
        startAmplitudeTicker()
        startAutoAdvanceCollector()
    }

    private var amplitudeTickerJob: Job? = null

        private fun startAmplitudeTicker() {
            amplitudeTickerJob?.cancel()
            amplitudeTickerJob = viewModelScope.launch {
                while (true) {
                    if (musicPlayer.isPlaying.value) {
                        val pos = musicPlayer.currentPosition.value
                        val dur = musicPlayer.duration.value
                        val data = _waveformData.value
                        if (dur > 0 && data.isNotEmpty()) {
                            val index = ((pos.toFloat() / dur.toFloat()) * (data.size - 1))
                                .toInt().coerceIn(0, data.size - 1)
                            val targetAmp = data[index].toFloat() / cachedMaxAmplitude.toFloat()

                            // 75% previous + 25% new — heavier smoothing absorbs small jitter,
                            // only sustained loud sections drive noticeable movement
                            _currentNormalizedAmplitude.value =
                                (_currentNormalizedAmplitude.value * 0.75f) + (targetAmp * 0.25f)
                        }
                        delay(16) // ~60 fps while playing
                    } else {
                        // Decay toward zero when paused.
                        val decayed = (_currentNormalizedAmplitude.value * 0.92f).coerceAtLeast(0f)
                        _currentNormalizedAmplitude.value = decayed
                        if (decayed < 0.005f) {
                            // Amplitude is effectively zero — suspend until playback resumes
                            // instead of spinning 60fps doing nothing.
                            _currentNormalizedAmplitude.value = 0f
                            musicPlayer.isPlaying.first { it }
                        } else {
                            delay(16) // still decaying — keep ticking until silence
                        }
                    }
                }
            }
        }

        /**
         * Observes [MusicPlayer.currentMediaItemIndex] so that waveform data and dominant
         * color are refreshed whenever the player advances to a new track automatically
         * (auto-advance, notification controls, hardware buttons). Uses [collectLatest] so a
         * rapid skip cancels the previous extraction before starting a new one.
         */
        private fun startAutoAdvanceCollector() {
            viewModelScope.launch {
                musicPlayer.currentMediaItemIndex.collectLatest { index ->
                    val track = _currentQueue.value.getOrNull(index) ?: return@collectLatest
                    // Skip if the track didn't actually change (avoids double side-effect
                    // when playTrack() already set _currentTrack before the listener fires).
                    if (_currentTrack.value?.id == track.id) return@collectLatest
                    _currentTrack.value = track
                    resetAmplitudeState()
                    launchWaveformExtraction(track)
                    launchColorExtraction(track)
                }
            }
        }

        private fun Track.toMediaItem(): MediaItem =
            MediaItem.Builder().setUri(mediaUri).setMediaId(id.toString()).build()

        fun playTracks(sourceTracks: List<Track>, startIndex: Int) {
            if (sourceTracks.isEmpty()) return
            try {
                val safeIndex = startIndex.coerceIn(0, sourceTracks.lastIndex)
                val track = sourceTracks[safeIndex]

                _currentQueue.value = sourceTracks
                _currentTrack.value = track
                resetAmplitudeState()

                val mediaItems = sourceTracks.map { it.toMediaItem() }
                musicPlayer.setPlaylist(mediaItems, safeIndex)

                launchWaveformExtraction(track)
                launchColorExtraction(track)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun seekToQueueIndex(index: Int) {
            val q = _currentQueue.value
            if (index !in q.indices) return
            val track = q[index]
            _currentTrack.value = track
            resetAmplitudeState()
            musicPlayer.seekToQueueIndex(index)
            launchWaveformExtraction(track)
            launchColorExtraction(track)
        }

        private fun resetAmplitudeState() {
            _waveformData.value = emptyList()
            cachedMaxAmplitude = 1
            _currentNormalizedAmplitude.value = 0f
        }

        private fun launchWaveformExtraction(track: Track) {
            waveformJob?.cancel()
            val waveformUri = Uri.parse(track.mediaUri)
            waveformJob = viewModelScope.launch {
                try {
                    val wf = waveformExtractor.extractWaveform(waveformUri)
                    _waveformData.value = wf
                    cachedMaxAmplitude = wf.maxOrNull()?.coerceAtLeast(1) ?: 1
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Failed to extract waveform", e)
                    _waveformData.value = emptyList()
                    cachedMaxAmplitude = 1
                }
            }
        }

        private fun launchColorExtraction(track: Track) {
            colorJob?.cancel()
            val loadTarget = track.mediaUri
            if (loadTarget.isNullOrEmpty()) {
                _playingTrackDominantColor.value = null
                return
            }
            colorJob = viewModelScope.launch {
                withContext(Dispatchers.Default) {
                    try {
                        val imageLoader = SingletonImageLoader.get(context)
                        val request = ImageRequest.Builder(context)
                            .data(AudioArtData(loadTarget, track.albumArtUri))
                            .size(100)
                            .build()

                        val result = imageLoader.execute(request)
                        if (result is SuccessResult) {
                            val bitmap = (result.image as? coil3.BitmapImage)?.bitmap
                            bitmap?.let { bmp ->
                                Palette.from(bmp).generate().dominantSwatch?.let { swatch ->
                                    _playingTrackDominantColor.value = Color(swatch.rgb)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        private fun seedCurrentTrack(track: Track) {
            _currentTrack.value = track
            resetAmplitudeState()
            launchWaveformExtraction(track)
            launchColorExtraction(track)
        }

        private suspend fun restorePlaybackSession(allTracks: List<Track>) {
            musicPlayer.awaitConnection()
            val byId = allTracks.associateBy { it.id }
            val live = musicPlayer.currentQueueSnapshot()
            if (!live.isEmpty) {
                // Case A: process survived — controller still has the queue. Reattach UI without
                // touching playback. mapNotNull filters tracks deleted mid-session; this can
                // desync _currentQueue length from the controller queue — a known tradeoff for
                // the process-alive fast path. coerceIn guards the OOB crash only.
                val tracks = live.mediaIds.mapNotNull { byId[it] }
                if (tracks.isEmpty()) return
                _currentQueue.value = tracks
                seedCurrentTrack(tracks[live.index.coerceIn(0, tracks.lastIndex)])
            } else {
                // Case B: full process death — rebuild queue paused from DataStore.
                val saved = sessionStore.session.first() ?: return
                val resolved = resolveSession(saved, byId) ?: run { sessionStore.clear(); return }
                _currentQueue.value = resolved.tracks
                musicPlayer.setPlaylistPaused(resolved.tracks.map { it.toMediaItem() }, resolved.index)
                musicPlayer.setShuffle(saved.shuffle)
                musicPlayer.setRepeatMode(saved.repeat)
                seedCurrentTrack(resolved.tracks[resolved.index])
            }
        }

        private fun startSessionPersistence() {
            viewModelScope.launch {
                combine(
                    _currentQueue,
                    musicPlayer.currentMediaItemIndex,
                    musicPlayer.shuffleModeEnabled,
                    musicPlayer.repeatMode
                ) { queue, index, shuffle, repeat ->
                    if (queue.isEmpty()) null
                    else PlaybackSession(queue.map { it.id }, index, shuffle, repeat)
                }
                .distinctUntilChanged()
                .collect { session ->
                    if (session == null) sessionStore.clear()
                    else sessionStore.save(session)
                }
            }
        }

        fun updateSearchQuery(query: String) { _searchQuery.value = query }

        fun togglePlayPause() {
            if (isPlaying.value) musicPlayer.pause() else musicPlayer.play()
        }

        fun skipToPrevious() { musicPlayer.skipToPrevious() }
        fun skipToNext() { musicPlayer.skipToNext() }

        fun seekTo(progress: Float) {
            val dur = musicPlayer.duration.value
            if (dur > 0) musicPlayer.seekTo((progress * dur).toLong())
        }

        fun toggleShuffle() { musicPlayer.toggleShuffle() }
        fun cycleRepeatMode() { musicPlayer.cycleRepeatMode() }

        fun moveQueueItem(from: Int, to: Int) {
            val current = _currentQueue.value.toMutableList()
            if (from < 0 || to < 0 || from >= current.size || to >= current.size || from == to) return
            val item = current.removeAt(from)
            current.add(to, item)
            _currentQueue.value = current
            musicPlayer.moveQueueItem(from, to)
        }

        override fun onCleared() {
            super.onCleared()
            waveformJob?.cancel()
            colorJob?.cancel()
            visualizerManager.destroy()
            musicPlayer.release()
        }
}
