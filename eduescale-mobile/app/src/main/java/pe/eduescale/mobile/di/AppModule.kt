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
import pe.eduescale.mobile.routing.RoadRouting
import pe.eduescale.mobile.routing.RoadRoutingPort
import pe.eduescale.mobile.activity.SchoolActivityStore
import pe.eduescale.mobile.activity.ReminderStore
import pe.eduescale.mobile.activity.ActivityBackupStore
import pe.eduescale.mobile.activity.SchoolVisitStore
import pe.eduescale.mobile.data.SchoolActivityDatabase
import pe.eduescale.mobile.data.SchoolActivityRepository
import pe.eduescale.mobile.data.SharedPreferencesSavedSearchStore
import pe.eduescale.mobile.data.SchoolFileImportStore
import pe.eduescale.mobile.data.AndroidSchoolFileImportStore
import pe.eduescale.mobile.ui.schools.SavedSearchStore
import pe.eduescale.mobile.data.SchoolFileStorage
import pe.eduescale.mobile.data.ActivityBackupFile
import pe.eduescale.mobile.data.AndroidActivityBackupFile
import pe.eduescale.mobile.data.SavedSearchFile
import pe.eduescale.mobile.data.AndroidSavedSearchFile
import pe.eduescale.mobile.data.StorageSpace
import pe.eduescale.mobile.data.AndroidStorageSpace
import pe.eduescale.mobile.reminders.AndroidReminderAlarmPort
import pe.eduescale.mobile.reminders.ReminderAlarmPort
import pe.eduescale.mobile.map.OfflineMapStore
import pe.eduescale.mobile.map.MapLibreOfflineMapStore
import pe.eduescale.mobile.ui.theme.ThemeSettingsStore
import pe.eduescale.mobile.ui.theme.SharedPreferencesThemeSettingsStore

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    internal fun provideSchoolActivityDatabase(
        @ApplicationContext context: Context,
    ): SchoolActivityDatabase = SchoolActivityDatabase.create(context)

    @Provides
    fun provideRoadRouting(routing: RoadRouting): RoadRoutingPort = routing

    @Provides
    fun provideSchoolActivityStore(repository: SchoolActivityRepository): SchoolActivityStore = repository

    @Provides
    fun provideReminderStore(repository: SchoolActivityRepository): ReminderStore = repository

    @Provides
    fun provideReminderAlarmPort(port: AndroidReminderAlarmPort): ReminderAlarmPort = port

    @Provides
    fun provideOfflineMapStore(store: MapLibreOfflineMapStore): OfflineMapStore = store

    @Provides
    fun provideThemeSettingsStore(store: SharedPreferencesThemeSettingsStore): ThemeSettingsStore = store

    @Provides
    fun provideActivityBackupStore(repository: SchoolActivityRepository): ActivityBackupStore = repository

    @Provides
    fun provideSchoolVisitStore(repository: SchoolActivityRepository): SchoolVisitStore = repository

    @Provides
    fun provideActivityBackupFile(file: AndroidActivityBackupFile): ActivityBackupFile = file

    @Provides
    fun provideSavedSearchStore(store: SharedPreferencesSavedSearchStore): SavedSearchStore = store

    @Provides
    fun provideSavedSearchFile(file: AndroidSavedSearchFile): SavedSearchFile = file

    @Provides
    fun provideStorageSpace(storage: AndroidStorageSpace): StorageSpace = storage

    @Provides
    fun provideSchoolFileImportStore(store: AndroidSchoolFileImportStore): SchoolFileImportStore = store

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
