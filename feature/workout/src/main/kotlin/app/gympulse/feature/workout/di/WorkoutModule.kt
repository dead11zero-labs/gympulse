package app.gympulse.feature.workout.di

import app.gympulse.core.workout.ActiveWorkoutForeground
import app.gympulse.feature.workout.service.ServiceActiveWorkoutForeground
import app.gympulse.feature.workout.timer.SystemTicker
import app.gympulse.feature.workout.timer.Ticker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkoutModule {
    // RestTimerSettings is bound in the app module (DataStore-backed, see SettingsAdaptersModule).
    @Binds
    abstract fun bindTicker(impl: SystemTicker): Ticker

    @Binds
    abstract fun bindActiveWorkoutForeground(impl: ServiceActiveWorkoutForeground): ActiveWorkoutForeground
}
