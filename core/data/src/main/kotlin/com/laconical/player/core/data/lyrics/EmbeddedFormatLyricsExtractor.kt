package com.laconical.player.core.data.lyrics

/**
 * Port for container-level embedded-lyrics extraction that needs Media3 (FLAC/OGG
 * VorbisComment). Implemented in :core:media so :core:data never depends on Media3;
 * MP3 ID3 USLT is handled separately by the pure-JVM parser in :core:model.
 */
interface EmbeddedFormatLyricsExtractor {

    /**
     * Returns raw lyrics text from container metadata, or null when absent/unsupported.
     * The text may itself be LRC-formatted — callers run it through LrcParser.
     */
    suspend fun extract(mediaUri: String): String?
}
