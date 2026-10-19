package app.gympulse.core.data.di

import app.gympulse.core.data.util.SystemTimeProvider
import app.gympulse.core.data.util.TimeProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun bindTimeProvider(impl: SystemTimeProvider): TimeProvider
}
