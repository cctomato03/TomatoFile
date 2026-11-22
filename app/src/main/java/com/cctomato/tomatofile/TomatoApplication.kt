package com.cctomato.tomatofile

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.cctomato.tomatofile.data.cache.RemoteCache
import com.cctomato.tomatofile.data.crypto.TokenCrypto
import com.cctomato.tomatofile.data.repo.FileRepository
import com.cctomato.tomatofile.data.store.SettingsStore
import com.cctomato.tomatofile.data.store.SourceStore

/** Manual dependency container: small enough that a DI framework would only add build risk. */
class AppContainer(context: Context) {
    val settings = SettingsStore(context)
    val crypto = TokenCrypto()
    val sources = SourceStore(context, crypto)
    val cache = RemoteCache(context, settings)
    val repository = FileRepository(cache)
}

class TomatoApplication : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .crossfade(true)
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image-cache"))
                .maxSizeBytes(256L * 1024L * 1024L)
                .build()
        }
        .respectCacheHeaders(false)
        .build()
}

val Context.tomatoContainer: AppContainer
    get() = (applicationContext as TomatoApplication).container