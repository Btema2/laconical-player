package com.laconical.player.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Fires a one-shot vibration of [durationMs] at default amplitude.
 *
 * minSdk 26 makes [VibrationEffect.createOneShot] unconditional — no legacy `vibrate(Long)`
 * fallback needed. [Compose's `LocalHapticFeedback`] only exposes fixed constants
 * (LongPress, TextHandleMove, ...) and cannot express the escalating custom durations used by
 * the miniplayer swipe-down-to-remove gesture, so the platform [Vibrator] is used directly.
 */
fun Context.vibrateOneShot(durationMs: Long) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
}
