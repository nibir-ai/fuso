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

rootProject.name = "Fuso"

include(":app")
include(":core:model")
include(":core:common")
include(":core:designsystem")
include(":core:ui")
include(":core:data")
include(":core:database")
include(":core:intelligence")
include(":feature:today")
include(":feature:journal")
include(":feature:calendar")
include(":feature:notes")
include(":feature:editor")
include(":feature:search")
include(":feature:settings")
