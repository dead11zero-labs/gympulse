# GymPulse – Hinweise für Agents

Offline-first Gym-Tracker für Android. Produktkonzept und Aufgabenplan liegen im Projekt unter
`/mnt/project-files/gympulse/` (konzept.md, aufgabenplan.md, uebungsdatenbanken.md).

## Stack
- Kotlin, Jetpack Compose, Material 3, AGP 9 mit eingebautem Kotlin (kein `kotlin-android`-Plugin, kein kapt).
- Hilt über KSP, Navigation Compose mit typsicheren `@Serializable`-Routen.
- Versionen nur in `gradle/libs.versions.toml`.
- compileSdk 37, targetSdk 36, minSdk 28, JDK 17.

## Module
- `app` – Activity, Scaffold mit Bottom Navigation, NavHost.
- `core:designsystem` – `GymPulseTheme`, Farben/Typo/Shapes, gemeinsame Komponenten. Screens nutzen nur
  `MaterialTheme`-Tokens, keine hartcodierten Farben oder Größen außerhalb dieses Moduls.
- `feature:*` – je ein Feature; stellt eine `@Serializable`-Route und `NavGraphBuilder.xyzScreen()` bereit.
- Convention-Plugins in `build-logic/convention`: `gympulse.android.application`, `.library`, `.compose`,
  `.feature` (Library + Compose + Hilt + Navigation + Designsystem), `gympulse.hilt`.

## Konventionen
- UI-Texte nur über `strings.xml`, immer Englisch (`values/`) und Deutsch (`values-de/`) pflegen.
- Gewicht immer in kg. Datenbank-IDs sind UUIDs, Tabellen tragen `created_at`, `updated_at`, `deleted_at`.
- Jede Aufgabe als eigener PR gegen `main`. CI (GitHub Actions) baut, testet und lintet jeden PR.
- Code-Kommentare und Commit-Messages auf Englisch oder Deutsch, aber pro Datei einheitlich.

## Bauen
`./gradlew assembleDebug testDebugUnitTest lintDebug`
