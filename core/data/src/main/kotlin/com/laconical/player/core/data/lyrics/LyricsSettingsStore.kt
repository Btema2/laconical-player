package com.laconical.player.core.data.lyrics

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Order in which lyrics sources are tried after the memory/Room caches miss. */
enum class LyricsSourcePriority {
    /** Embedded tags → sibling .lrc → LRCLIB (default). */
    EMBEDDED_FIRST,

    /** Sibling .lrc → embedded tags → LRCLIB. */
    LOCAL_FIRST,

    /** LRCLIB (when network allowed) → embedded tags → sibling .lrc. */
    API_FIRST
}

interface LyricsSettingsStore {
    /** Whether LRCLIB network lookups are allowed. Privacy-first: defaults to false. */
    val networkEnabled: Flow<Boolean>
    val sourcePriority: Flow<LyricsSourcePriority>
    suspend fun setNetworkEnabled(enabled: Boolean)
    suspend fun setSourcePriority(priority: LyricsSourcePriority)
}

private val Context.lyricsSettingsDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "lyrics_settings")

@Singleton
class DataStoreLyricsSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) : LyricsSettingsStore {

    private object Keys {
        val NETWORK_ENABLED = booleanPreferencesKey("network_enabled")
        val SOURCE_PRIORITY = stringPreferencesKey("source_priority")
    }

    private val safeData: Flow<Preferences> = context.lyricsSettingsDataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences())
            else throw e
        }

    override val networkEnabled: Flow<Boolean> =
        safeData.map { it[Keys.NETWORK_ENABLED] ?: false }

    override val sourcePriority: Flow<LyricsSourcePriority> = safeData.map { prefs ->
        prefs[Keys.SOURCE_PRIORITY]
            ?.let { raw -> runCatching { LyricsSourcePriority.valueOf(raw) }.getOrNull() }
            ?: LyricsSourcePriority.EMBEDDED_FIRST
    }

    override suspend fun setNetworkEnabled(enabled: Boolean) {
        context.lyricsSettingsDataStore.edit { it[Keys.NETWORK_ENABLED] = enabled }
    }

    override suspend fun setSourcePriority(priority: LyricsSourcePriority) {
        context.lyricsSettingsDataStore.edit { it[Keys.SOURCE_PRIORITY] = priority.name }
    }
}
