package com.laconical.player.core.model

data class TrackAudioDetails(
    val track: Track,
    val filePath: String? = null,
    val fileSizeFormatted: String? = null,
    val mimeType: String? = null,
    val dateAddedFormatted: String? = null,
    val bitrateKbps: Int? = null,
    val sampleRateHz: Int? = null,
    val bitDepthBits: Int? = null,
    val channels: String? = null,
    val codec: String? = null,
    val albumArtist: String? = null,
    val composer: String? = null,
    val year: String? = null,
    val genre: String? = null,
    val discNumber: String? = null,
)
