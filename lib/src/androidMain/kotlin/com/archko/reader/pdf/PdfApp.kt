package com.archko.reader.pdf

import android.app.Application
import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.allowRgb565
import coil3.request.bitmapConfig
import coil3.request.crossfade
import com.archko.reader.pdf.cache.CustomImageFetcher
import com.archko.reader.pdf.util.CrashHandler
import com.tencent.bugly.library.Bugly
import com.tencent.bugly.library.BuglyBuilder
import com.tencent.mmkv.MMKV

/**
 * @author: archko 2025/1/4 :21:21
 */
public class PdfApp : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        app = this

        MMKV.initialize(this)
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler())

        val appId = "d34dc863a0"
        val appKey = "3bb5b51f-0626-4267-9f7b-2d2fc468fbdd"
        val builder = BuglyBuilder(appId, appKey)
        Bugly.init(applicationContext, builder, false)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(this)
            .crossfade(true)
            .allowRgb565(true)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    //.directory(FileHelpers.getExternalCacheDir(this).resolve("image_cache"))
                    .maxSizePercent(0.04)
                    .maxSizeBytes(200L * 1024 * 1024)
                    .build()
            }
            .components {
                add(
                    CustomImageFetcher.Factory()
                )
            }
            .build()
    }

    public companion object {
        public var app: PdfApp? = null
            private set

        //一张图片4-5mb,200mb大概缓存50张
        public const val MAX_CACHE: Int = 300 * 1024 * 1024
    }
}