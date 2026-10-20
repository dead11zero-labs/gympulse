package app.gympulse.core.health

import app.gympulse.core.data.health.HealthDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class HealthConnectModule {
    @Binds
    abstract fun bindHealthDataSource(impl: HealthConnectDataSource): HealthDataSource
}
