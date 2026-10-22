plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Protokoll zwischen Telefon und Wear-OS-App: DTOs, Data-Layer-Pfade, Snapshot-Mapping und
// Reducer für den optimistischen Zustand auf der Uhr. Reines Kotlin, damit beide Apps es teilen
// und alles ohne Gerät getestet werden kann.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

// Die CI ruft `testDebugUnitTest` auf; in einem JVM-Modul heißt der Task `test`.
tasks.register("testDebugUnitTest") {
    group = "verification"
    description = "Alias für test, damit die CI die Tests dieses JVM-Moduls ausführt."
    dependsOn(tasks.named("test"))
}
