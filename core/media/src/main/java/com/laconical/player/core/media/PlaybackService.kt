package com.laconical.player.core.media

import android.content.Intent
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Media3 [MediaSessionService] that manages the [MediaSession] lifecycle.
 *
 * [ExoPlayer] is a Hilt singleton so it survives service restarts (e.g. when the
 * user swipes the app from recents while paused and then reopens it). [MediaSession]
 * is created fresh in [onCreate] and released in [onDestroy] — it is NOT a Hilt
 * singleton. Releasing it here is safe because a new session is created the next
 * time the service starts, while the same player instance is reused.
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var player: ExoPlayer

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = mediaSession?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        // Release the session IPC resource. Do NOT call player.release() here —
        // the player is a Hilt singleton and must remain usable after the service
        // is destroyed and recreated.
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
