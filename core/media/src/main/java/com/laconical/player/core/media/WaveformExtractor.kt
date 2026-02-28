package com.laconical.player.core.media

import android.content.Context
import android.net.Uri
import com.linc.amplituda.Amplituda
import com.linc.amplituda.AmplitudaResult
import com.linc.amplituda.exceptions.io.AmplitudaIOException
import com.linc.amplituda.exceptions.processing.AmplitudaProcessingException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class WaveformExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val amplituda = Amplituda(context)

    suspend fun extractWaveform(uriString: String): List<Int> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            try {
                // Amplituda supports both file paths and URIs. 
                // We'll treat it as a path if it starts with '/', otherwise as a URI.
                val audioSource = if (uriString.startsWith("/")) {
                    uriString
                } else {
                    uriString // Amplituda also handles String URIs
                }

                amplituda.processAudio(audioSource).get({ result ->
                    val amplitudes = result.amplitudesAsList()
                    continuation.resume(amplitudes)
                }, { exception ->
                    continuation.resumeWithException(exception)
                })
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }
}
