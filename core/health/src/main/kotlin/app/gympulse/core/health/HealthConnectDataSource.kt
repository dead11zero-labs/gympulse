package app.gympulse.core.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import app.gympulse.core.data.health.HealthActivity
import app.gympulse.core.data.health.HealthAvailability
import app.gympulse.core.data.health.HealthDataSource
import app.gympulse.core.data.health.HealthReadType
import app.gympulse.core.data.health.HealthSleep
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

    override fun readPermission(type: HealthReadType): String = HealthConnectPermissions.read(type)

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
        // Ohne Abschnitte: Samsung Health übernimmt Einheiten mit Abschnitten anderer Apps nicht
        // zuverlässig. Die Übungen stehen in den Notizen.
        client.insertRecords(listOf(HealthRecordMapper.toExerciseSessionRecordWithoutSegments(workout)))
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

    override suspend fun aggregateActivity(
        start: Instant,
        end: Instant,
        types: Set<HealthReadType>,
    ): HealthActivity {
        val client = clientOrNull() ?: return HealthActivity()
        val metrics = buildSet<AggregateMetric<*>> {
            if (HealthReadType.HEART_RATE in types) {
                add(HeartRateRecord.BPM_AVG)
                add(HeartRateRecord.BPM_MAX)
            }
            if (HealthReadType.STEPS in types) add(StepsRecord.COUNT_TOTAL)
            if (HealthReadType.ACTIVE_CALORIES in types) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
        }
        if (metrics.isEmpty()) return HealthActivity()
        val result = client.aggregate(
            AggregateRequest(metrics = metrics, timeRangeFilter = TimeRangeFilter.between(start, end)),
        )
        return HealthActivity(
            averageHeartRateBpm = result[HeartRateRecord.BPM_AVG],
            maxHeartRateBpm = result[HeartRateRecord.BPM_MAX],
            steps = result[StepsRecord.COUNT_TOTAL],
            activeCaloriesKcal = result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories,
        )
    }

    override suspend fun readLatestSleep(from: Instant, to: Instant): HealthSleep? {
        val client = clientOrNull() ?: return null
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(from, to),
                ascendingOrder = false,
                pageSize = SLEEP_PAGE_SIZE,
            ),
        )
        return response.records
            .filter { it.endTime.isAfter(from) && !it.endTime.isAfter(to) }
            .maxByOrNull { it.endTime }
            ?.let(HealthRecordMapper::toHealthSleep)
    }

    private fun clientOrNull(): HealthConnectClient? =
        if (availability() == HealthAvailability.AVAILABLE) HealthConnectClient.getOrCreate(context) else null

    private companion object {
        const val SLEEP_PAGE_SIZE = 10
    }
}

/** Berechtigungen und Berechtigungsdialog von Health Connect. */
object HealthConnectPermissions {
    /** Paket der Health-Connect-App auf Android 9 bis 13. */
    const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"

    val REQUIRED: Set<String> = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
    )

    /** Lese-Berechtigung je Datentyp; Herzfrequenz, Schritte, Kalorien und Schlaf sind optional. */
    fun read(type: HealthReadType): String = HealthPermission.getReadPermission(HealthRecordMapper.recordType(type))

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
