package com.laconical.player.core.model.lyrics

/**
 * Returns the index of the line currently being sung at [positionMs]: the last line whose
 * timestamp is <= the position. Returns -1 before the first line or when [lines] is empty.
 *
 * [lines] must be sorted by timestamp with non-null timestamps (as produced by [LrcParser]
 * and [Id3UsltParser]); lines with null timestamps are treated as unreachable.
 */
fun currentLineIndex(lines: List<LyricsLine>, positionMs: Long): Int {
    var low = 0
    var high = lines.size - 1
    var result = -1
    while (low <= high) {
        val mid = (low + high) ushr 1
        val ts = lines[mid].timestampMs ?: Long.MAX_VALUE
        if (ts <= positionMs) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return result
}
