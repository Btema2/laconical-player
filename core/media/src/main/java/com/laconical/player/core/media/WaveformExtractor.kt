package com.laconical.player.core.media

import android.content.Context
import android.net.Uri
import com.linc.amplituda.Amplituda
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    // Amplituda is not thread-safe. The Mutex ensures only one extraction runs at a
    // time, preventing concurrent calls when the user switches tracks rapidly.
    // When a coroutine is cancelled (track changed), the Mutex is released immediately
    // so the next extraction can start without waiting for the old one to finish.
    // Note: Amplituda has no cancel API, so the underlying IO thread completes its
    // work regardless — but its callback becomes a safe no-op on a cancelled continuation.
    private val mutex = Mutex()

    // Accepts a content URI. Amplituda 2.3.1 has no Uri overload, so we open an
    // InputStream via ContentResolver — works correctly under scoped storage.
    suspend fun extractWaveform(uri: Uri): List<Int> = withContext(Dispatchers.IO) {
        mutex.withLock {
            suspendCancellableCoroutine { continuation ->
                try {
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("Cannot open audio stream: $uri")
                    amplituda.processAudio(stream).get(
                        { result ->
                            continuation.resume(result.amplitudesAsList())
                        },
                        { exception ->
                            continuation.resumeWithException(exception)
                        }
                    )
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }
        }
    }
}
