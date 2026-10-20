package app.gympulse.core.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import app.gympulse.core.data.health.HealthAvailability
import app.gympulse.core.data.health.HealthDataSource
import app.gympulse.core.data.health.HealthWeight
import app.gympulse.core.data.health.HealthWorkout
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** [HealthDataSource] auf Basis des Jetpack-Clients `androidx.health.connect:connect-client`. */
@Singleton
class HealthConnectDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : HealthDataSource {

    override val requiredPermissions: Set<String> = HealthConnectPermissions.REQUIRED

    override fun availability(): HealthAvailability =
        when (HealthConnectClient.getSdkStatus(context, HealthConnectPermissions.PROVIDER_PACKAGE)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.UPDATE_REQUIRED
            // Bis Android 13 lässt sich die Health-Connect-App nachinstallieren, ab 14 ist sie Teil des Systems.
            else -> if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                HealthAvailability.NOT_INSTALLED
            } else {
                HealthAvailability.UNSUPPORTED
            }
        }

    override suspend fun grantedPermissions(): Set<String> {
        val client = clientOrNull() ?: return emptySet()
        return client.permissionController.getGrantedPermissions()
    }

    override suspend fun insertWorkout(workout: HealthWorkout) {
        val client = clientOrNull() ?: return
        client.insertRecords(listOf(HealthRecordMapper.toExerciseSessionRecord(workout)))
    }

    override suspend fun readLatestWeight(since: Instant): HealthWeight? {
        val client = clientOrNull() ?: return null
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = TimeRangeFilter.after(since),
                ascendingOrder = false,
                pageSize = 1,
            ),
        )
        return response.records.firstOrNull()?.let(HealthRecordMapper::toHealthWeight)
    }

    private fun clientOrNull(): HealthConnectClient? =
        if (availability() == HealthAvailability.AVAILABLE) HealthConnectClient.getOrCreate(context) else null
}

/** Berechtigungen und Berechtigungsdialog von Health Connect. */
object HealthConnectPermissions {
    /** Paket der Health-Connect-App auf Android 9 bis 13. */
    const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"

    val REQUIRED: Set<String> = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
    )

    /** Vertrag für `rememberLauncherForActivityResult`; Ergebnis sind die erteilten Berechtigungen. */
    fun requestContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract(PROVIDER_PACKAGE)

    /** Play-Store-Seite von Health Connect, um es zu installieren oder zu aktualisieren. */
    fun playStoreIntent(): Intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("market://details?id=$PROVIDER_PACKAGE&url=healthconnect%3A%2F%2Fonboarding"),
    ).setPackage("com.android.vending")
        .putExtra("overlay", true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
