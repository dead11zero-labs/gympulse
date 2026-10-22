package app.gympulse.core.settings.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import app.gympulse.core.settings.DataStoreIntegrationSettings
import app.gympulse.core.settings.DataStoreUserSettingsRepository
import app.gympulse.core.settings.IntegrationSettings
import app.gympulse.core.settings.UserSettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {
    @Binds
    @Singleton
    abstract fun bindUserSettingsRepository(impl: DataStoreUserSettingsRepository): UserSettingsRepository

    @Binds
    @Singleton
    abstract fun bindIntegrationSettings(impl: DataStoreIntegrationSettings): IntegrationSettings

    companion object {
        private const val FILE_NAME = "user_settings"

        // Pro Datei darf es nur eine DataStore-Instanz geben, daher Singleton.
        @Provides
        @Singleton
        fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(FILE_NAME) }
    }
}
