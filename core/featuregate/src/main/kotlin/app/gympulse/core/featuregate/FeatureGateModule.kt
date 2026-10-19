package app.gympulse.core.featuregate

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FeatureGateModule {
    @Binds
    @Singleton
    abstract fun bindFeatureGate(impl: DefaultFeatureGate): FeatureGate

    @Binds
    @Singleton
    abstract fun bindLocalDefaults(impl: AllEnabledLocalFeatureDefaults): LocalFeatureDefaults

    @Binds
    @Singleton
    abstract fun bindRemoteConfig(impl: NoOpRemoteFeatureConfig): RemoteFeatureConfig

    @Binds
    @Singleton
    abstract fun bindEntitlements(impl: NoOpEntitlementProvider): EntitlementProvider
}
