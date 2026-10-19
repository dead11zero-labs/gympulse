package app.gympulse

import android.app.Application
import app.gympulse.core.data.seed.SeedImporter
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class GymPulseApplication : Application() {
    @Inject lateinit var seedImporter: SeedImporter

    // Läuft so lange wie der Prozess; Fehler eines Jobs brechen andere nicht ab.
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { seedImporter.importIfNeeded() }
    }
}
