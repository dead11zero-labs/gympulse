package app.gympulse.core.data.di

import app.gympulse.core.data.repository.DefaultProgressPhotoRepository
import app.gympulse.core.data.repository.ProgressPhotoRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ProgressPhotoModule {
    @Binds
    abstract fun bindProgressPhotoRepository(impl: DefaultProgressPhotoRepository): ProgressPhotoRepository
}
