package com.laconical.player.core.data.lyrics

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/** One LRCLIB search/get candidate. */
data class LrcLibResult(
    val trackName: String,
    val artistName: String,
    val albumName: String,
    val durationSec: Double,
    val instrumental: Boolean,
    val plainLyrics: String?,
    val syncedLyrics: String?
)

/**
 * Minimal LRCLIB (lrclib.net) client. `/api/get` is an exact lookup (404 = miss, not an
 * error); `/api/search` returns ranked-by-us candidates. Calls are serialised single-in-flight
 * with a small minimum gap — polite to a free public API.
 *
 * IOExceptions and non-2xx (other than get's 404) propagate to the repository, which maps
 * them to a NetworkError distinct from a genuine miss.
 */
@Singleton
class LrcLibClient @Inject constructor(
    private val httpClient: OkHttpClient
) {

    private val requestMutex = Mutex()
    private var lastRequestAtMs = 0L

    /** Exact match using full metadata — duration is LRCLIB's strongest disambiguator. */
    suspend fun getExact(artist: String, title: String, album: String, durationSec: Int): LrcLibResult? {
        val url = BASE_URL.toHttpUrl().newBuilder()
            .addPathSegments("api/get")
            .addQueryParameter("artist_name", artist)
            .addQueryParameter("track_name", title)
            .addQueryParameter("album_name", album)
            .addQueryParameter("duration", durationSec.toString())
            .build()
        val body = execute(url, missOn404 = true) ?: return null
        return parseResult(JSONObject(body))
    }

    suspend fun search(artist: String, title: String): List<LrcLibResult> {
        val url = BASE_URL.toHttpUrl().newBuilder()
            .addPathSegments("api/search")
            .addQueryParameter("track_name", title)
            .apply { if (artist.isNotBlank()) addQueryParameter("artist_name", artist) }
            .build()
        val body = execute(url, missOn404 = true) ?: return emptyList()
        val array = JSONArray(body)
        return buildList {
            for (i in 0 until array.length()) {
                add(parseResult(array.getJSONObject(i)))
            }
        }
    }

    private suspend fun execute(url: HttpUrl, missOn404: Boolean): String? =
        requestMutex.withLock {
            val sinceLast = System.currentTimeMillis() - lastRequestAtMs
            if (sinceLast in 0 until MIN_REQUEST_GAP_MS) delay(MIN_REQUEST_GAP_MS - sinceLast)
            try {
                withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", USER_AGENT)
                        .build()
                    httpClient.newCall(request).execute().use { response ->
                        when {
                            response.isSuccessful -> response.body?.string()
                            response.code == 404 && missOn404 -> null
                            else -> throw IOException("LRCLIB HTTP ${response.code}")
                        }
                    }
                }
            } finally {
                lastRequestAtMs = System.currentTimeMillis()
            }
        }

    private fun parseResult(json: JSONObject): LrcLibResult = LrcLibResult(
        trackName = json.optString("trackName"),
        artistName = json.optString("artistName"),
        albumName = json.optString("albumName"),
        durationSec = json.optDouble("duration", 0.0),
        instrumental = json.optBoolean("instrumental", false),
        plainLyrics = json.optStringOrNull("plainLyrics"),
        syncedLyrics = json.optStringOrNull("syncedLyrics")
    )

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (isNull(name)) null else optString(name).ifBlank { null }

    private companion object {
        const val BASE_URL = "https://lrclib.net"

        // LRCLIB asks clients to identify themselves.
        const val USER_AGENT = "LaconicalPlayer/1.1 (https://github.com/Btema2/Laconical-Player)"

        const val MIN_REQUEST_GAP_MS = 300L
    }
}
