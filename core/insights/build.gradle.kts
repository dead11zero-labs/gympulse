plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Klassische Trainingsauswertung (Schicht 1 des Plans "Lokale KI"): reines Kotlin ohne
// Android- und Room-Abhängigkeit, damit die Logik mit schnellen JVM-Tests abgesichert ist.
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
    api(projects.core.model)
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

// CI ruft `testDebugUnitTest` auf; reine JVM-Module kennen diese Task nicht. Der Alias sorgt
// dafür, dass die Tests dieses Moduls trotzdem in jedem PR laufen.
tasks.register("testDebugUnitTest") {
    group = "verification"
    description = "Runs the JVM unit tests of this module (alias for CI)."
    dependsOn(tasks.named("test"))
}
