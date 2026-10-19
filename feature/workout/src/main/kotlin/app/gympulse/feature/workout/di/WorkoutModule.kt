package app.gympulse.feature.workout.di

import app.gympulse.feature.workout.timer.DefaultRestTimerSettings
import app.gympulse.feature.workout.timer.RestTimerSettings
import app.gympulse.feature.workout.timer.SystemTicker
import app.gympulse.feature.workout.timer.Ticker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkoutModule {
    /** Replace with the DataStore-backed implementation once the settings feature exists. */
    @Binds
    abstract fun bindRestTimerSettings(impl: DefaultRestTimerSettings): RestTimerSettings

    @Binds
    abstract fun bindTicker(impl: SystemTicker): Ticker
}
