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
- `core:featuregate` – `Feature`, `FeatureGate` und Hilt-Bindings; entscheidet aus lokalen Defaults, Remote-Config und Entitlements, ob ein Feature aktiv ist.
- `core:health` – Health Connect (`connect-client`): Implementierung von `HealthDataSource` aus `core:data/health`,
  Manifest-Berechtigungen; hinter `Feature.HEALTH_CONNECT` (Opt-in, Default aus).
- `core:cloud` – optionales Cloud-Backup: Supabase (auth-kt, postgrest-kt), Google-Login per Credential Manager. Schnittstellen
  (`CloudAuth`, `CloudBackupRemote`, `LocalBackupSource`) und `CloudBackupSync` liegen in `core:data`. Konfiguration über
  gradle.properties (`gympulseSupabaseUrl`, `gympulseSupabasePublishableKey`, `gympulseGoogleWebClientId`), SQL unter `supabase/migrations/`.
- `core:wearprotocol` – reines Kotlin/JVM: Protokoll Telefon ↔ Uhr (`@Serializable`-Snapshot und -Kommandos, `WearPaths`,
  Mapping `WorkoutSession → SessionSnapshot`, `WatchWorkoutReducer` für den optimistischen Zustand). Plan: `wearos-plan.md`.
- `core:workout` – `ActiveWorkoutController`: einziger Schreibpfad für das laufende Workout (Mutex, Sätze, Finish inkl.
  Zusammenfassung/PRs/Health-Export) und persistenter `RestTimer` (`endsAt` in DataStore). Funktioniert ohne offenen Screen;
  ViewModel, Notification und Wear-Bridge nutzen ihn.
- `wear` – Wear-OS-App (Companion, `standalone=false`): Compose for Wear OS (Material 3 für Wear), Hilt, minSdk 33, gleiche
  `applicationId` wie `app` (Data Layer). Plugin `gympulse.wear.application`. Das Telefon bleibt Source of Truth, Plan siehe `wearos-plan.md`.
  **Ausnahme:** Das Wear-Theme (`app.gympulse.wear.theme`) hat eigene Tokens (Wear-`ColorScheme`, Kinetic-Farben), weil Wear Material 3 ein
  eigenes Token-System hat; sonst gelten dieselben Regeln (keine hartcodierten Farben/Größen außerhalb des Themes, Strings DE+EN).
- `feature:*` – je ein Feature; stellt eine `@Serializable`-Route und `NavGraphBuilder.xyzScreen()` bereit.
- Convention-Plugins in `build-logic/convention`: `gympulse.android.application`, `.library`, `.compose`,
  `.feature` (Library + Compose + Hilt + Navigation + Designsystem), `gympulse.hilt`, `gympulse.wear.application` (Wear-App, ohne Phone-Material3).

## Design (Material 3)
- Referenz ist der Material-3-Skill (`/material-3`) bzw. m3.material.io; das finale Design von Jörg folgt.
- Nur Tokens: `MaterialTheme.colorScheme` (immer in Paaren wie `primary`/`onPrimary`), `MaterialTheme.typography`
  (Display/Headline/Title/Body/Label), `MaterialTheme.shapes`, Abstände aus `Spacing` (8-dp-Raster).
- Material3-Composables statt Eigenbau (`Button`, `Card`, `ListItem`, `NavigationSuiteScaffold` …).
- Tiefe über tonale Flächen (`surfaceContainer*`), keine Schatten als Standard. Trenner mit `outlineVariant`.
- Adaptiv: Navigation Bar < 600 dp, Rail ab 600 dp; Inhalte auf großen Screens auf `Spacing.maxContentWidth` begrenzen.
- Touch-Ziele mindestens 48 dp, Kontrast prüfen, Icons mit `contentDescription`, wenn sie allein stehen.
- Aktives Navigationsziel: gefülltes Icon, sonst Outline-Icon. Labels immer anzeigen.

## Konventionen
- UI-Texte nur über `strings.xml`, immer Englisch (`values/`) und Deutsch (`values-de/`) pflegen.
- Gewicht immer in kg. Datenbank-IDs sind UUIDs, Tabellen tragen `created_at`, `updated_at`, `deleted_at`.
- Jede Aufgabe als eigener PR gegen `main`. CI (GitHub Actions) baut, testet und lintet jeden PR.
- Code-Kommentare und Commit-Messages auf Englisch oder Deutsch, aber pro Datei einheitlich.

## Bauen
`./gradlew assembleDebug testDebugUnitTest lintDebug`

## Release
- `.github/workflows/release.yml` baut `:app:assembleRelease` und `:wear:assembleRelease` (manuell, bei Push auf `main` und bei Tags `v*`; Tags erzeugen ein GitHub-Release mit beiden APKs).
- Signatur über die Secrets `GYMPULSE_KEYSTORE_BASE64`, `GYMPULSE_KEYSTORE_PASSWORD`, `GYMPULSE_KEY_ALIAS`, `GYMPULSE_KEY_PASSWORD`. Fehlen sie, wird mit dem Debug-Schlüssel signiert. Keystores nie committen.
- Phone- und Wear-APK teilen sich die `applicationId` und müssen denselben Schlüssel haben (Data Layer). Die Signing-Konfiguration liegt zentral in `build-logic` (`ReleaseSigning.kt`), nicht in den Modulen.
- Jeder Build auf `main` veröffentlicht die APKs zusätzlich als Pre-Release `latest-main` (direkter Download ohne ZIP, funktioniert auch am Handy): `gympulse-*.apk` (Handy) und `gympulse-wear-*.apk` (Wear OS).
