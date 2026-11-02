package org.idktech.lumen

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy

class LumenApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        org.idktech.lumen.data.AppCache.ensureHomeService(this)
    }

    /** Bounded caches: 2 GB of RAM on this TV, so posters never get to pile up in memory. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizeBytes(40 * 1024 * 1024).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(150L * 1024 * 1024).build() }
        .diskCachePolicy(CachePolicy.ENABLED)
        .crossfade(200)
        .respectCacheHeaders(false)
        .build()
}
