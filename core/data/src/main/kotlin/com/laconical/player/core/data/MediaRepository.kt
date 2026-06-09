package com.laconical.player.core.data

import com.laconical.player.core.model.Track
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    suspend fun getTracks(): List<Track>
    fun getTracksFlow(batchSize: Int = 25): Flow<List<Track>>
}
