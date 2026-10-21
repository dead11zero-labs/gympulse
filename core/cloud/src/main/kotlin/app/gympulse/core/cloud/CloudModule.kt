package app.gympulse.core.cloud

import app.gympulse.core.data.cloud.CloudAuth
import app.gympulse.core.data.cloud.CloudBackupRemote
import app.gympulse.core.data.cloud.LocalBackupSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class CloudModule {
    @Binds
    abstract fun bindCloudAuth(impl: SupabaseCloudAuth): CloudAuth

    @Binds
    abstract fun bindCloudBackupRemote(impl: SupabaseCloudBackupRemote): CloudBackupRemote

    @Binds
    abstract fun bindLocalBackupSource(impl: BackupManagerLocalSource): LocalBackupSource
}
