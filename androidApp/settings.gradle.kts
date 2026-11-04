pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "Ledger"

include(":core") // pure Kotlin: the rules of Ledger, no Android
include(":app")  // the Android app: screens, storage, wiring
