package com.laconical.player

import android.app.Application
import android.util.Log
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import com.laconical.player.core.media.MediaPreWarmer
import com.laconical.player.ui.AudioAlbumArtFetcher
import com.laconical.player.ui.AudioAlbumArtKeyer
import okio.Path.Companion.toOkioPath
import com.linc.amplituda.Amplituda
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class LaconicalApp : Application(), SingletonImageLoader.Factory {

    private val preWarmScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        MediaPreWarmer.prewarm(this)
        preWarmAmplituda()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .components {
                add(AudioAlbumArtFetcher.Factory())
                add(AudioAlbumArtKeyer())
            }
            .build()
    }

    // Force Amplituda's native lib load + JIT on a silent buffer so the first
    // real waveform extraction after cold start doesn't pay that cost on the hot path.
    private fun preWarmAmplituda() {
        preWarmScope.launch {
            try {
                Amplituda(this@LaconicalApp).processAudio(ByteArray(1024)).get(
                    { /* ignored — silent buffer can't decode */ },
                    { /* ignored */ }
                )
            } catch (e: Exception) {
                Log.w("LaconicalApp", "Amplituda pre-warm failed", e)
            }
        }
    }
}
