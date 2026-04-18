package com.laconical.player.core.media

import android.media.audiofx.Visualizer
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Manager responsible for extracting real-time audio data from ExoPlayer.
 * Captures waveform data and calculates a beat pulse value for UI animations.
 * Attached to the specific audioSessionId to avoid RECORD_AUDIO permission.
 */
@Singleton
class AudioVisualizerManager @Inject constructor(
    private val player: ExoPlayer
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var visualizer: Visualizer? = null

    private val _waveform = MutableStateFlow(FloatArray(0))
    val waveform = _waveform.asStateFlow()

    private val _beatPulse = MutableStateFlow(0f)
    val beatPulse = _beatPulse.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                initializeVisualizer()
            } else {
                releaseVisualizer()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
                releaseVisualizer()
            }
        }
    }

    init {
        player.addListener(playerListener)
        if (player.isPlaying) {
            initializeVisualizer()
        }
    }

    @Volatile private var isVisualizerGeneratingRealData = false
    private var fallbackJob: Job? = null

    private fun processWaveform(bytes: ByteArray) {
        val normalized = FloatArray(bytes.size.coerceAtLeast(1))
        var sumSquares = 0f
        var isAllSilent = true
        
        for (i in bytes.indices) {
            val unsignedValue = bytes[i].toInt() and 0xFF
            // Silence is usually 128 (center) or 0
            if (unsignedValue != 128 && unsignedValue != 0) {
                isAllSilent = false
            }
            
            normalized[i] = unsignedValue / 255f
            val deviation = (unsignedValue - 128).toFloat()
            sumSquares += deviation * deviation
        }
        
        isVisualizerGeneratingRealData = !isAllSilent

        if (isVisualizerGeneratingRealData) {
            val rms = if (bytes.isNotEmpty()) sqrt(sumSquares / bytes.size) else 0f
            val pulse = (rms / 40f).coerceIn(0f, 1f)
            
            scope.launch {
                _waveform.value = normalized
                _beatPulse.value = pulse
            }
        }
    }

    private fun startFallbackLoop() {
        if (fallbackJob?.isActive == true) return
        // Loop runs on Dispatchers.Default (from the class scope) so sin/cos and
        // FloatArray allocation stay off the UI thread. ExoPlayer reads must
        // happen on its owning looper, so they hop to Main briefly.
        fallbackJob = scope.launch {
            while (isActive) {
                // Skip all fake-wave computation when the real Visualizer is producing data.
                if (isVisualizerGeneratingRealData) {
                    delay(100)
                    continue
                }
                val (playing, time) = withContext(Dispatchers.Main) {
                    player.isPlaying to player.currentPosition
                }
                if (playing) {
                    val fakeWave = FloatArray(64)
                    for (i in fakeWave.indices) {
                        // Generate a dynamic, aesthetic sine/perlin-like wave based on time and index
                        val phase = time * 0.005f + i * 0.2f
                        val value = 0.5f + 0.3f * kotlin.math.sin(phase.toDouble()).toFloat() + 0.1f * kotlin.math.cos(time * 0.01 + i * 0.5).toFloat()
                        fakeWave[i] = value.coerceIn(0f, 1f)
                    }

                    // Generate heartbeat pulse: (1.0f + 0.05f * sin(time)) scaled to 0..1 for UI
                    // Sine goes from -1 to 1.
                    val pulseSine = kotlin.math.sin(time * 0.004).toFloat()
                    val fakePulse = (pulseSine + 1f) / 2f * 0.8f // 0 to 0.8 range

                    _waveform.value = fakeWave
                    _beatPulse.value = fakePulse
                } else {
                    // Decay pulse smoothly when paused
                    _beatPulse.value = (_beatPulse.value * 0.8f).coerceAtLeast(0f)
                }
                delay(32) // ~30fps update
            }
        }
    }

    private fun initializeVisualizer() {
        if (visualizer != null) return

        val sessionId = player.audioSessionId
        if (sessionId == 0) {
            // No audio session yet — fall back to the synthetic wave so the UI
            // still pulses, and try Visualizer again when playback reports a session.
            startFallbackLoop()
            return
        }

        try {
            visualizer = Visualizer(sessionId).apply {
                captureSize = 128 // Smaller capture size for faster/smoother fallback check
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        v: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        waveform?.let { processWaveform(it) }
                    }

                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {}
                }, Visualizer.getMaxCaptureRate() / 2, true, false)
                enabled = true
            }
            // Fallback loop stays alive but idles when real data is flowing — it
            // takes over if the Visualizer starts reporting silence.
            startFallbackLoop()
        } catch (e: Exception) {
            e.printStackTrace()
            isVisualizerGeneratingRealData = false
            startFallbackLoop()
        }
    }

    fun releaseVisualizer() {
        visualizer?.apply {
            enabled = false
            release()
        }
        visualizer = null
        isVisualizerGeneratingRealData = false
        fallbackJob?.cancel()
        fallbackJob = null
        _beatPulse.value = 0f
    }


    /**
     * Call this when the application or service is being destroyed.
     */
    fun destroy() {
        player.removeListener(playerListener)
        releaseVisualizer()
        scope.cancel()
    }
}
