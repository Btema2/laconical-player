package com.laconical.player.core.media

import android.content.Intent
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val COMMAND_TOGGLE_SHUFFLE = "com.laconical.player.TOGGLE_SHUFFLE"
private const val COMMAND_CYCLE_REPEAT = "com.laconical.player.CYCLE_REPEAT"

/**
 * Media3 [MediaSessionService] that manages the [MediaSession] lifecycle.
 *
 * [ExoPlayer] is a Hilt singleton so it survives service restarts (e.g. when the
 * user swipes the app from recents while paused and then reopens it). [MediaSession]
 * is created fresh in [onCreate] and released in [onDestroy] — it is NOT a Hilt
 * singleton. Releasing it here is safe because a new session is created the next
 * time the service starts, while the same player instance is reused.
 *
 * Shuffle and repeat are exposed to the system notification via media button
 * preferences. Taps come back through [MediaSession.Callback.onCustomCommand];
 * icon state stays in sync via a [Player.Listener] that rebuilds the button list
 * whenever shuffle or repeat mode changes.
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var player: ExoPlayer

    private var mediaSession: MediaSession? = null

    private val playerListener = object : Player.Listener {
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            refreshMediaButtons()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            refreshMediaButtons()
        }
    }

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            .setMediaButtonPreferences(buildMediaButtons(player.shuffleModeEnabled, player.repeatMode))
            .build()
        player.addListener(playerListener)
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
        player.removeListener(playerListener)
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    private fun refreshMediaButtons() {
        val session = mediaSession ?: return
        session.setMediaButtonPreferences(
            buildMediaButtons(player.shuffleModeEnabled, player.repeatMode)
        )
    }

    private fun buildMediaButtons(
        shuffleOn: Boolean,
        repeatMode: Int
    ): List<CommandButton> {
        val shuffle = CommandButton.Builder(
            if (shuffleOn) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF
        )
            .setDisplayName(if (shuffleOn) "Shuffle on" else "Shuffle off")
            .setSessionCommand(SessionCommand(COMMAND_TOGGLE_SHUFFLE, Bundle.EMPTY))
            .build()

        val repeatIcon = when (repeatMode) {
            Player.REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE
            Player.REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL
            else -> CommandButton.ICON_REPEAT_OFF
        }
        val repeatName = when (repeatMode) {
            Player.REPEAT_MODE_ONE -> "Repeat one"
            Player.REPEAT_MODE_ALL -> "Repeat all"
            else -> "Repeat off"
        }
        val repeat = CommandButton.Builder(repeatIcon)
            .setDisplayName(repeatName)
            .setSessionCommand(SessionCommand(COMMAND_CYCLE_REPEAT, Bundle.EMPTY))
            .build()

        return listOf(shuffle, repeat)
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                .buildUpon()
                .add(SessionCommand(COMMAND_TOGGLE_SHUFFLE, Bundle.EMPTY))
                .add(SessionCommand(COMMAND_CYCLE_REPEAT, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            return when (customCommand.customAction) {
                COMMAND_TOGGLE_SHUFFLE -> {
                    player.shuffleModeEnabled = !player.shuffleModeEnabled
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                COMMAND_CYCLE_REPEAT -> {
                    player.repeatMode = when (player.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                else -> super.onCustomCommand(session, controller, customCommand, args)
            }
        }
    }
}
