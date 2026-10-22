package app.gympulse.wear.data

import android.content.Context
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Scope, der so lange lebt wie der Prozess der Uhr-App. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WearAppScope

@Module
@InstallIn(SingletonComponent::class)
abstract class WearBindingsModule {
    @Binds
    abstract fun bindWearChannel(impl: DataClientWearChannel): WearChannel
}

@Module
@InstallIn(SingletonComponent::class)
object WearProvidersModule {
    @Provides
    @Singleton
    fun dataClient(@ApplicationContext context: Context): DataClient = Wearable.getDataClient(context)

    @Provides
    @Singleton
    fun nodeClient(@ApplicationContext context: Context): NodeClient = Wearable.getNodeClient(context)

    @Provides
    @Singleton
    @WearAppScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
