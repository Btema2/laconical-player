package com.laconical.player.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.laconical.player.core.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// ----- Data models -----

data class PlaybackSession(
    val trackIds: List<Long>,
    val index: Int,
    val shuffle: Boolean,
    val repeat: Int,
)

data class ResolvedSession(val tracks: List<Track>, val index: Int)

// Mirrors Player.REPEAT_MODE_OFF (= 0) without pulling media3 into core:data
private const val REPEAT_MODE_OFF = 0

// ----- Interface -----

interface PlaybackSessionStore {
    val session: Flow<PlaybackSession?>
    suspend fun save(session: PlaybackSession)
    suspend fun clear()
}

// ----- DataStore extension -----

private val Context.playbackSessionDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "playback_session")

// ----- Implementation -----

@Singleton
class DataStorePlaybackSessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : PlaybackSessionStore {

    private object Keys {
        val TRACK_IDS = stringPreferencesKey("track_ids")
        val INDEX = intPreferencesKey("current_index")
        val SHUFFLE = booleanPreferencesKey("shuffle")
        val REPEAT = intPreferencesKey("repeat_mode")
    }

    override val session: Flow<PlaybackSession?> = context.playbackSessionDataStore.data.map { prefs ->
        val raw = prefs[Keys.TRACK_IDS] ?: return@map null
        val ids = raw.split(",")
            .filter { it.isNotBlank() }
            .mapNotNull { it.trim().toLongOrNull() }
        if (ids.isEmpty()) return@map null
        PlaybackSession(
            trackIds = ids,
            index = prefs[Keys.INDEX] ?: 0,
            shuffle = prefs[Keys.SHUFFLE] ?: false,
            repeat = prefs[Keys.REPEAT] ?: REPEAT_MODE_OFF,
        )
    }

    override suspend fun save(session: PlaybackSession) {
        context.playbackSessionDataStore.edit { prefs ->
            prefs[Keys.TRACK_IDS] = session.trackIds.joinToString(",")
            prefs[Keys.INDEX] = session.index
            prefs[Keys.SHUFFLE] = session.shuffle
            prefs[Keys.REPEAT] = session.repeat
        }
    }

    override suspend fun clear() {
        context.playbackSessionDataStore.edit { prefs ->
            prefs.clear()
        }
    }
}

// ----- Pure resolution function (package-level, Android-free, easily testable) -----

fun resolveSession(saved: PlaybackSession, byId: Map<Long, Track>): ResolvedSession? {
    val filteredTracks = saved.trackIds.mapNotNull { byId[it] }
    if (filteredTracks.isEmpty()) return null

    val savedCurrentId = saved.trackIds.getOrNull(saved.index)
    val newIndex = if (savedCurrentId != null && byId.containsKey(savedCurrentId)) {
        // Current track survived — find its new position in the filtered list
        val survivingIds = saved.trackIds.filter { byId.containsKey(it) }
        survivingIds.indexOf(savedCurrentId).coerceIn(0, filteredTracks.lastIndex)
    } else {
        // Current track was deleted or index was out of range — count how many
        // surviving ids had original positions before saved.index
        val survivingBeforeIndex = saved.trackIds
            .take(saved.index)
            .count { byId.containsKey(it) }
        survivingBeforeIndex.coerceIn(0, filteredTracks.lastIndex)
    }

    return ResolvedSession(filteredTracks, newIndex)
}
