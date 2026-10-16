pluginManagement {
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

rootProject.name = "UltimateAlarm"
include(":app")
include(":core:designsystem")
include(":core:scheduling")
include(":core:data")
include(":feature:alarms")
include(":feature:ringing")
include(":feature:holidays")
include(":feature:sounds")
include(":feature:clock")
include(":feature:backup")
include(":feature:onboarding")
