package com.laconical.player.core.data

import com.laconical.player.core.model.Track

interface MediaRepository {
    suspend fun getTracks(): List<Track>
}
