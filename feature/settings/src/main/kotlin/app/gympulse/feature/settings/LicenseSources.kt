package app.gympulse.feature.settings

/**
 * Eine Quelle in der Lizenzliste. [descriptionRes] beschreibt, wofür die Quelle genutzt wird.
 */
internal data class LicenseEntry(
    val name: String,
    val license: String,
    val url: String?,
    val descriptionRes: Int? = null,
)

/**
 * Quellen für "Über die App".
 *
 * Übungsdaten: Die Angaben stammen aus dem Seed-Asset `core/database/src/main/assets/seed/exercises_v1.json`
 * (Feld `source`: free-exercise-db, Lizenz Unlicense), aus `tools/seed/data/equipment_templates_v1.json`
 * (Feld `license`: CC0-1.0) und aus `core/database/src/main/assets/seed/LICENSES.md`
 * (deutsche Namen sind eigene Inhalte, CC0-1.0). Bei neuen Seed-Quellen hier ergänzen.
 *
 * Bibliotheken sind fest eingetragen; alle gelisteten stehen unter Apache-2.0.
 */
internal object LicenseSources {
    private const val APACHE_2 = "Apache-2.0"

    val exerciseData: List<LicenseEntry> = listOf(
        LicenseEntry(
            name = "free-exercise-db",
            license = "Unlicense",
            url = "https://github.com/yuhonas/free-exercise-db",
            descriptionRes = R.string.about_source_free_exercise_db,
        ),
        LicenseEntry(
            name = "GymPulse",
            license = "CC0-1.0",
            url = null,
            descriptionRes = R.string.about_source_gympulse_content,
        ),
    )

    val libraries: List<LicenseEntry> = listOf(
        LicenseEntry("Jetpack Compose, Material 3", APACHE_2, "https://developer.android.com/jetpack/compose"),
        LicenseEntry("Room", APACHE_2, "https://developer.android.com/training/data-storage/room"),
        LicenseEntry("Hilt (Dagger)", APACHE_2, "https://dagger.dev/hilt"),
        LicenseEntry("Coil", APACHE_2, "https://coil-kt.github.io/coil/"),
        LicenseEntry("Kotlin", APACHE_2, "https://kotlinlang.org"),
        LicenseEntry("Navigation Compose", APACHE_2, "https://developer.android.com/guide/navigation"),
        LicenseEntry("DataStore", APACHE_2, "https://developer.android.com/topic/libraries/architecture/datastore"),
    )
}
