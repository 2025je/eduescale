package pe.eduescale.mobile

import android.app.Application
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil

class EduScaleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        HttpRequestUtil.setOkHttpClient(
            OkHttpClient.Builder()
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
                .build(),
        )
    }
}
