package com.laconical.player.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackAudioDetailsTest {
    @Test
    fun testTrackAudioDetailsCreation() {
        val track = Track(
            id = 1L,
            title = "Test Title",
            artist = "Test Artist",
            album = "Test Album",
            durationMs = 180000L,
            mediaUri = "content://media/external/audio/media/1",
        )
        val details = TrackAudioDetails(
            track = track,
            filePath = "/sdcard/Music/test.mp3",
            fileSizeFormatted = "4.2 MB",
            mimeType = "audio/mpeg",
            dateAddedFormatted = "2026-08-04",
            bitrateKbps = 320,
            sampleRateHz = 44100,
            bitDepthBits = 16,
            channels = "Stereo (2.0)",
            codec = "MP3",
            albumArtist = "Test Artist",
            composer = "Test Composer",
            year = "2026",
            genre = "Rock",
            discNumber = "1",
        )

        assertEquals("Test Title", details.track.title)
        assertEquals(320, details.bitrateKbps)
        assertEquals("Stereo (2.0)", details.channels)
    }
}
