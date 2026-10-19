plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Reine Kotlin-Modelle ohne Android-Abhängigkeit, damit App, Wear OS und später
// ein Backend-Client sie teilen können.
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
    testImplementation(libs.junit)
}
