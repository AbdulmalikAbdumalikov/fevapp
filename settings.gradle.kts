pluginManagement {
    // Convention plugins shared by all modules live in build-logic.
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

rootProject.name = "fevapp"

include(":app")

include(":core:designsystem")
include(":core:model")
include(":core:common")
include(":core:database")

include(":engine:timeline")
include(":engine:render")
include(":engine:effects")

include(":feature:home")
include(":feature:editor")
include(":feature:export")
