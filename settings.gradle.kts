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

rootProject.name = "AMPlusPlus"
include(":app")
include(":backdrop", ":glass")
include(":core", ":host-api", ":hook-runtime", ":host-applemusic")
include(":plugin-api", ":plugin-runtime")
include(":vivo-plugin")
project(":vivo-plugin").projectDir = file("examples/vivo-plugin")
