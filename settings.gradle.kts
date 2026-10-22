pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "GymPulse"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":wear")
include(":core:model")
include(":core:database")
include(":core:data")
include(":core:designsystem")
include(":core:featuregate")
include(":core:settings")
include(":core:backup")
include(":core:health")
include(":core:cloud")
include(":core:insights")
include(":feature:dashboard")
include(":feature:workout")
include(":feature:plans")
include(":feature:library")
include(":feature:calendar")
include(":feature:settings")
