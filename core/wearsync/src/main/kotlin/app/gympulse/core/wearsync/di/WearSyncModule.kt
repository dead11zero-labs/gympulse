package app.gympulse.core.wearsync.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import app.gympulse.core.workout.RestAlertPolicy
import app.gympulse.core.wearsync.CapabilityWatchAppStatus
import app.gympulse.core.wearsync.ControllerWorkoutPort
import app.gympulse.core.wearsync.DataStoreWatchHeartRateStore
import app.gympulse.core.wearsync.DataStoreWearSyncStore
import app.gympulse.core.wearsync.PlayServicesWearDataClient
import app.gympulse.core.wearsync.RepositoryWearSnapshotSources
import app.gympulse.core.wearsync.RemoteWatchAppLauncher
import app.gympulse.core.wearsync.WatchAppLauncher
import app.gympulse.core.wearsync.WatchAppStatus
import app.gympulse.core.wearsync.WatchHeartRateStore
import app.gympulse.core.wearsync.WearDistribution
import app.gympulse.core.wearsync.WearPlayListing
import app.gympulse.core.wearsync.WearDataClient
import app.gympulse.core.wearsync.WearRestAlertPolicy
import app.gympulse.core.wearsync.WearSnapshotSources
import app.gympulse.core.wearsync.WearSyncStore
import app.gympulse.core.wearsync.WearWorkoutPort
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** Prozessweiter Scope der Wear-Anbindung (Publisher, liegengebliebene Kommandos). */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class WearSyncScope

@Module
@InstallIn(SingletonComponent::class)
abstract class WearSyncBindings {
    @Binds
    abstract fun bindDataClient(impl: PlayServicesWearDataClient): WearDataClient

    @Binds
    abstract fun bindWatchAppStatus(impl: CapabilityWatchAppStatus): WatchAppStatus

    @Binds
    abstract fun bindWatchAppLauncher(impl: RemoteWatchAppLauncher): WatchAppLauncher

    @Binds
    abstract fun bindWorkoutPort(impl: ControllerWorkoutPort): WearWorkoutPort

    @Binds
    abstract fun bindSnapshotSources(impl: RepositoryWearSnapshotSources): WearSnapshotSources

    @Binds
    abstract fun bindRestAlertPolicy(impl: WearRestAlertPolicy): RestAlertPolicy
}

@Module
@InstallIn(SingletonComponent::class)
object WearSyncProviders {
    private const val STORE_FILE = "wear_sync"
    private const val HEART_RATE_FILE = "wear_heart_rate"

    @Provides
    @Singleton
    fun dataClient(@ApplicationContext context: Context): DataClient = Wearable.getDataClient(context)

    @Provides
    @Singleton
    fun capabilityClient(@ApplicationContext context: Context): CapabilityClient = Wearable.getCapabilityClient(context)

    @Provides
    @WearPlayListing
    fun playListingAvailable(): Boolean = WearDistribution.PLAY_LISTING_AVAILABLE

    @Provides
    @Singleton
    fun nodeClient(@ApplicationContext context: Context): NodeClient = Wearable.getNodeClient(context)

    @Provides
    @Singleton
    @WearSyncScope
    fun scope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Eigene DataStore-Datei, eine Instanz pro Datei, daher Singleton.
    @Provides
    @Singleton
    fun store(@ApplicationContext context: Context): WearSyncStore =
        DataStoreWearSyncStore(PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(STORE_FILE) })

    @Provides
    @Singleton
    fun heartRateStore(@ApplicationContext context: Context): WatchHeartRateStore =
        DataStoreWatchHeartRateStore(PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(HEART_RATE_FILE) })
}
