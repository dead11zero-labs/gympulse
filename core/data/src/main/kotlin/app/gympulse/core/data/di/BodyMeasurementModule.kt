package app.gympulse.core.data.di

import app.gympulse.core.data.repository.BodyMeasurementRepository
import app.gympulse.core.data.repository.DefaultBodyMeasurementRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class BodyMeasurementModule {
    @Binds
    abstract fun bindBodyMeasurementRepository(impl: DefaultBodyMeasurementRepository): BodyMeasurementRepository
}
