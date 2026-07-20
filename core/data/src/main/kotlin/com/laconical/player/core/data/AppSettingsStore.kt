package com.laconical.player.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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

// ----- Interface -----

/**
 * Small general-purpose app preferences store, distinct from the feature-specific
 * LyricsSettingsStore/PlaybackSessionStore. The album sort order is persisted by name
 * (a plain String) rather than the AlbumSortOrder enum itself, because that enum lives
 * in :app (ui/SortOrder.kt) which :core:data cannot depend on — the name<->enum mapping
 * is done by the caller (AlbumsViewModel).
 */
interface AppSettingsStore {
    /** Raw AlbumSortOrder.name(), or null if never set. */
    val albumSortOrder: Flow<String?>
    suspend fun setAlbumSortOrder(name: String)
}

// ----- DataStore extension -----

private val Context.appSettingsDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "app_settings")

// ----- Implementation -----

@Singleton
class DataStoreAppSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppSettingsStore {

    private object Keys {
        val ALBUM_SORT_ORDER = stringPreferencesKey("album_sort_order")
    }

    private val safeData: Flow<Preferences> = context.appSettingsDataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences())
            else throw e
        }

    override val albumSortOrder: Flow<String?> =
        safeData.map { it[Keys.ALBUM_SORT_ORDER] }

    override suspend fun setAlbumSortOrder(name: String) {
        context.appSettingsDataStore.edit { it[Keys.ALBUM_SORT_ORDER] = name }
    }
}
