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

    private fun initializeVisualizer() {
        if (visualizer != null) return
        
        val sessionId = player.audioSessionId
        if (sessionId == 0) return // Invalid session

        try {
            visualizer = Visualizer(sessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1] // Max capture size
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        v: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        waveform?.let { processWaveform(it) }
                    }

                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        // Not used for now
                    }
                }, Visualizer.getMaxCaptureRate() / 2, true, false)
                enabled = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun processWaveform(bytes: ByteArray) {
        val normalized = FloatArray(bytes.size)
        var sumSquares = 0f
        
        for (i in bytes.indices) {
            // Visualizer returns unsigned 8-bit data [0, 255]
            val unsignedValue = bytes[i].toInt() and 0xFF
            
            // Normalize to [0, 1] for Seekbar drawing
            normalized[i] = unsignedValue / 255f
            
            // For Beat Pulse: calculate deviation from center (128)
            val deviation = (unsignedValue - 128).toFloat()
            sumSquares += deviation * deviation
        }
        
        // RMS calculation for beat pulse
        val rms = if (bytes.isNotEmpty()) sqrt(sumSquares / bytes.size) else 0f
        // Scale RMS to a reasonable [0, 1] range for UI pulse (typical RMS for music is < 50)
        val pulse = (rms / 40f).coerceIn(0f, 1f)
        
        scope.launch {
            _waveform.value = normalized
            _beatPulse.value = pulse
        }
    }

    fun releaseVisualizer() {
        visualizer?.apply {
            enabled = false
            release()
        }
        visualizer = null
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
