package com.laconical.player.core.media

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

/**
 * Spins up the PlaybackService + ExoPlayer singletons at application start so the
 * user's first tap doesn't pay the MediaController IPC + service construction
 * cost on the interactive path. A throwaway controller is connected, then
 * released — the service and the ExoPlayer singleton stay warm for the real
 * MusicPlayer instance that the ViewModel will create.
 */
object MediaPreWarmer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun prewarm(context: Context) {
        scope.launch {
            try {
                val token = SessionToken(
                    context,
                    ComponentName(context, PlaybackService::class.java)
                )
                val controller = MediaController.Builder(context, token)
                    .buildAsync()
                    .await()
                controller.release()
            } catch (e: Exception) {
                Log.w("MediaPreWarmer", "MediaController pre-warm failed", e)
            }
        }
    }
}
