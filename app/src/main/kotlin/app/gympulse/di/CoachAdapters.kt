package app.gympulse.di

import app.gympulse.core.ai.ActiveWorkoutProbe
import app.gympulse.core.workout.ActiveWorkoutController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Meldet dem KI-Coach, ob ein Workout läuft: Dann wird kein Modell geladen. */
class ControllerActiveWorkoutProbe @Inject constructor(
    private val controller: ActiveWorkoutController,
) : ActiveWorkoutProbe {
    override suspend fun isWorkoutActive(): Boolean = controller.activeSession.first() != null
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CoachAdaptersModule {
    @Binds
    abstract fun bindActiveWorkoutProbe(impl: ControllerActiveWorkoutProbe): ActiveWorkoutProbe
}
