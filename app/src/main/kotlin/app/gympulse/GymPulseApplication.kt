package app.gympulse

import android.app.Application
import app.gympulse.core.data.cloud.AutoCloudBackup
import app.gympulse.core.data.health.HealthConnectSync
import app.gympulse.core.data.seed.SeedImporter
import app.gympulse.core.wearsync.WearSync
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class GymPulseApplication : Application() {
    @Inject lateinit var seedImporter: SeedImporter
    @Inject lateinit var autoCloudBackup: AutoCloudBackup
    @Inject lateinit var healthConnectSync: HealthConnectSync
    @Inject lateinit var wearSync: WearSync

    // Läuft so lange wie der Prozess; Fehler eines Jobs brechen andere nicht ab.
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { seedImporter.importIfNeeded() }
        // Optional (Einstellungen > Cloud-Backup): höchstens einmal pro App-Start, Fehler bleiben still.
        applicationScope.launch { autoCloudBackup.runOnce() }
        // Health Connect (opt-in): jüngstes Körpergewicht bei jedem Start übernehmen.
        applicationScope.launch { healthConnectSync.importLatestWeight() }
        // Wear OS (Feature.WEAR_APP): Snapshots für die Uhr, liegengebliebene Kommandos. Läuft auch,
        // wenn der WearableListenerService den Prozess geweckt hat.
        wearSync.start()
    }
}
