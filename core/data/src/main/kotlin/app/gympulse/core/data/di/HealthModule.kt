package app.gympulse.core.data.di

import app.gympulse.core.data.health.FinishedWorkoutExporter
import app.gympulse.core.data.health.HealthConnectSync
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Die Android-Implementierung von HealthDataSource bindet `core:health`. */
@Module
@InstallIn(SingletonComponent::class)
abstract class HealthModule {
    @Binds
    abstract fun bindFinishedWorkoutExporter(impl: HealthConnectSync): FinishedWorkoutExporter
}
