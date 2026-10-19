package app.gympulse.feature.library.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class LibraryModule {
    @Binds
    abstract fun bindLibraryRepository(impl: DefaultLibraryRepository): LibraryRepository
}
