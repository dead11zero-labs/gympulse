package app.gympulse.core.workout.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import app.gympulse.core.workout.timer.DataStoreRestTimerStore
import app.gympulse.core.workout.timer.RestTimerStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Process-wide scope of the active workout: writes and follow-up work (Health Connect export)
 * keep running when the screen that triggered them is gone.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ActiveWorkoutScope

@Module
@InstallIn(SingletonComponent::class)
object WorkoutCoreModule {
    private const val REST_TIMER_FILE = "rest_timer"

    @Provides
    @Singleton
    @ActiveWorkoutScope
    fun provideActiveWorkoutScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // One DataStore instance per file, hence singleton. Kept out of the graph as a plain
    // DataStore<Preferences> so it does not clash with the settings DataStore.
    @Provides
    @Singleton
    fun provideRestTimerStore(@ApplicationContext context: Context): RestTimerStore =
        DataStoreRestTimerStore(
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(REST_TIMER_FILE) },
        )
}
