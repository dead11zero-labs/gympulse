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
- `feature:*` – je ein Feature; stellt eine `@Serializable`-Route und `NavGraphBuilder.xyzScreen()` bereit.
- Convention-Plugins in `build-logic/convention`: `gympulse.android.application`, `.library`, `.compose`,
  `.feature` (Library + Compose + Hilt + Navigation + Designsystem), `gympulse.hilt`.

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
