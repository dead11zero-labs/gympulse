package app.gympulse.core.data.di

import app.gympulse.core.data.health.FinishedWorkoutExporter
import app.gympulse.core.data.health.GatedWorkoutExporter
import app.gympulse.core.data.health.HealthInsights
import app.gympulse.core.data.health.HealthSleepReader
import app.gympulse.core.data.health.HealthStepsReader
import app.gympulse.core.data.health.WorkoutHealthReader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Die Android-Implementierung von HealthDataSource bindet `core:health`. */
@Module
@InstallIn(SingletonComponent::class)
abstract class HealthModule {
    @Binds
    abstract fun bindFinishedWorkoutExporter(impl: GatedWorkoutExporter): FinishedWorkoutExporter

    @Binds
    abstract fun bindWorkoutHealthReader(impl: HealthInsights): WorkoutHealthReader

    @Binds
    abstract fun bindHealthStepsReader(impl: HealthInsights): HealthStepsReader

    @Binds
    abstract fun bindHealthSleepReader(impl: HealthInsights): HealthSleepReader
}
