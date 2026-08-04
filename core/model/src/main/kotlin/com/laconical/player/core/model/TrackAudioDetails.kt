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
    val spectrogramFrequencies: FloatArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TrackAudioDetails

        if (track != other.track) return false
        if (filePath != other.filePath) return false
        if (fileSizeFormatted != other.fileSizeFormatted) return false
        if (mimeType != other.mimeType) return false
        if (dateAddedFormatted != other.dateAddedFormatted) return false
        if (bitrateKbps != other.bitrateKbps) return false
        if (sampleRateHz != other.sampleRateHz) return false
        if (bitDepthBits != other.bitDepthBits) return false
        if (channels != other.channels) return false
        if (codec != other.codec) return false
        if (albumArtist != other.albumArtist) return false
        if (composer != other.composer) return false
        if (year != other.year) return false
        if (genre != other.genre) return false
        if (discNumber != other.discNumber) return false
        if (spectrogramFrequencies != null) {
            if (other.spectrogramFrequencies == null) return false
            if (!spectrogramFrequencies.contentEquals(other.spectrogramFrequencies)) return false
        } else if (other.spectrogramFrequencies != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = track.hashCode()
        result = 31 * result + (filePath?.hashCode() ?: 0)
        result = 31 * result + (fileSizeFormatted?.hashCode() ?: 0)
        result = 31 * result + (mimeType?.hashCode() ?: 0)
        result = 31 * result + (dateAddedFormatted?.hashCode() ?: 0)
        result = 31 * result + (bitrateKbps ?: 0)
        result = 31 * result + (sampleRateHz ?: 0)
        result = 31 * result + (bitDepthBits ?: 0)
        result = 31 * result + (channels?.hashCode() ?: 0)
        result = 31 * result + (codec?.hashCode() ?: 0)
        result = 31 * result + (albumArtist?.hashCode() ?: 0)
        result = 31 * result + (composer?.hashCode() ?: 0)
        result = 31 * result + (year?.hashCode() ?: 0)
        result = 31 * result + (genre?.hashCode() ?: 0)
        result = 31 * result + (discNumber?.hashCode() ?: 0)
        result = 31 * result + (spectrogramFrequencies?.contentHashCode() ?: 0)
        return result
    }
}
