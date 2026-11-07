package pe.eduescale.mobile

import android.app.Application
import java.io.File
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil

class EduEscaleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        HttpRequestUtil.setOkHttpClient(createMapHttpClient(cacheDir))
    }
}

internal fun createMapHttpClient(cacheDirectory: File): OkHttpClient =
    OkHttpClient.Builder()
        .cache(
            Cache(
                File(cacheDirectory, MAP_TILE_CACHE_DIRECTORY),
                MAP_TILE_CACHE_SIZE_BYTES,
            ),
        )
        .addInterceptor { chain ->
            val request = chain.request()
                .newBuilder()
                .header(
                    "User-Agent",
                    "EduEscaleAndroid/1.0 (https://github.com/2025je/eduescale)",
                )
                .build()
            chain.proceed(request)
        }
        .build()

private const val MAP_TILE_CACHE_DIRECTORY = "map-tile-cache"
internal const val MAP_TILE_CACHE_SIZE_BYTES = 128L * 1024 * 1024
