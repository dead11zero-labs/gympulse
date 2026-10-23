package app.gympulse.core.ai.di

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import app.gympulse.core.ai.ActivityManagerDeviceMemory
import app.gympulse.core.ai.DeviceMemory
import app.gympulse.core.ai.ActiveWorkoutProbe
import app.gympulse.core.ai.DownloadedModelLocator
import app.gympulse.core.ai.GatedInsightNarrator
import app.gympulse.core.ai.LiteRtLmEngineFactory
import app.gympulse.core.ai.LiteRtLmNarrator
import app.gympulse.core.ai.LlmEngineFactory
import app.gympulse.core.ai.LlmEngineManager
import app.gympulse.core.ai.ModelLocator
import app.gympulse.core.featuregate.Feature
import app.gympulse.core.featuregate.FeatureGate
import app.gympulse.core.insights.narration.InsightNarrator
import app.gympulse.core.insights.narration.SourceAwareNarrator
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Bindings für den lokalen KI-Coach. `InsightNarrator` ist der [GatedInsightNarrator]: Das Modell
 * ([LiteRtLmNarrator]) kommt nur bei aktivem `Feature.AI_COACH`, vorhandenem Modell und ohne laufendes
 * Workout zum Zug, sonst formuliert der `TemplateNarrator`. Den [ActiveWorkoutProbe] liefert die App.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    abstract fun bindModelLocator(impl: DownloadedModelLocator): ModelLocator

    @Binds
    abstract fun bindDeviceMemory(impl: ActivityManagerDeviceMemory): DeviceMemory

    @Binds
    abstract fun bindEngineFactory(impl: LiteRtLmEngineFactory): LlmEngineFactory

    @Binds
    abstract fun bindInsightNarrator(impl: GatedInsightNarrator): InsightNarrator

    @Binds
    abstract fun bindSourceAwareNarrator(impl: GatedInsightNarrator): SourceAwareNarrator

    companion object {
        @Provides
        @Singleton
        fun provideEngineManager(
            @ApplicationContext context: Context,
            factory: LlmEngineFactory,
            modelLocator: ModelLocator,
            deviceMemory: DeviceMemory,
        ): LlmEngineManager {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val manager = LlmEngineManager(factory, modelLocator, deviceMemory, scope)
            // App im Hintergrund oder Speicherdruck: Engine sofort freigeben statt 60 s zu warten.
            context.registerComponentCallbacks(
                object : ComponentCallbacks2 {
                    override fun onTrimMemory(level: Int) {
                        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
                            scope.launch { manager.releaseIfIdle() }
                        }
                    }

                    override fun onConfigurationChanged(newConfig: Configuration) = Unit

                    @Deprecated("Deprecated in Java")
                    override fun onLowMemory() {
                        scope.launch { manager.releaseIfIdle() }
                    }
                },
            )
            return manager
        }

        @Provides
        @Singleton
        fun provideGatedInsightNarrator(
            featureGate: FeatureGate,
            workoutProbe: ActiveWorkoutProbe,
            manager: LlmEngineManager,
        ): GatedInsightNarrator = GatedInsightNarrator(
            isCoachEnabled = { featureGate.isEnabled(Feature.AI_COACH) },
            workoutProbe = workoutProbe,
            precheck = manager::precheck,
            llmFactory = { onFallback -> LiteRtLmNarrator(manager, onFallback = onFallback) },
        )

        @Provides
        @Singleton
        fun provideLiteRtLmNarrator(manager: LlmEngineManager): LiteRtLmNarrator = LiteRtLmNarrator(manager)
    }
}
