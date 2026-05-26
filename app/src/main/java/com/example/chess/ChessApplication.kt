package com.example.chess

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import okhttp3.OkHttpClient

class ChessApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(SvgDecoder.Factory())
            }
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val originalRequest = chain.request()
                        val newRequest = originalRequest.newBuilder()
                            .header(
                                "User-Agent",
                                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                            )
                            .build()
                        chain.proceed(newRequest)
                    }
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(true)
            .build()
    }
}
