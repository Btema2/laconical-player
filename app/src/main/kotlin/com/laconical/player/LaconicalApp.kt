package com.laconical.player

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.laconical.player.ui.AudioAlbumArtFetcher
import com.laconical.player.ui.AudioAlbumArtKeyer
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LaconicalApp : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(AudioAlbumArtFetcher.Factory())
                add(AudioAlbumArtKeyer())
            }
            .build()
    }
}
