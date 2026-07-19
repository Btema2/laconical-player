package com.laconical.player.ui.screens

/**
 * Privacy meter for Settings → Privacy.
 *
 * Each online feature the user opts into is a [PrivacyTradeoff]. More enabled
 * tradeoffs lower the meter. Today only online lyrics exists; future network
 * features (scrobbling, etc.) register as additional tradeoffs.
 */
data class PrivacyTradeoff(
    val id: String,
    val enabled: Boolean,
)

enum class PrivacyLevel(
    val label: String,
    val emoji: String,
    /** 0f = worst privacy, 1f = best */
    val fraction: Float,
) {
    ULTRA_SUPER_HIGH("Ultra Super High", "❤️‍🔥", 1.0f),
    HIGH("High", "😀", 0.8f),
    MEDIUM("Medium", "🙂", 0.55f),
    LOW("Low", "😐", 0.35f),
    MINIMAL("Minimal", "😬", 0.15f),
}

fun computePrivacyLevel(tradeoffs: List<PrivacyTradeoff>): PrivacyLevel {
    val enabledCount = tradeoffs.count { it.enabled }
    return when (enabledCount) {
        0 -> PrivacyLevel.ULTRA_SUPER_HIGH
        1 -> PrivacyLevel.HIGH
        2 -> PrivacyLevel.MEDIUM
        3 -> PrivacyLevel.LOW
        else -> PrivacyLevel.MINIMAL
    }
}

/** Builds the current tradeoff list. Extend when new online features land. */
fun privacyTradeoffs(lyricsNetworkEnabled: Boolean): List<PrivacyTradeoff> = listOf(
    PrivacyTradeoff(id = "lyrics_network", enabled = lyricsNetworkEnabled),
)

fun PrivacyLevel.displaySubtitle(): String = "$label $emoji"
