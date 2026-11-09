package pe.eduescale.mobile.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient
import org.maplibre.android.offline.OfflineManager
import pe.eduescale.mobile.createMapHttpClient
import pe.eduescale.mobile.data.SchoolFileStorage

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideMapHttpClient(@ApplicationContext context: Context): OkHttpClient =
        createMapHttpClient(context.cacheDir)

    @Provides
    @Singleton
    fun provideSchoolFileStorage(@ApplicationContext context: Context): SchoolFileStorage =
        SchoolFileStorage(context.filesDir)

    @Provides
    fun provideOfflineManager(@ApplicationContext context: Context): OfflineManager =
        OfflineManager.getInstance(context)
}
