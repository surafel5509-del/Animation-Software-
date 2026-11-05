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

rootProject.name = "Animation Studio"

include(":app")
include(":core")
include(":core-ui")
include(":graphics-engine")
include(":vector-engine")
include(":animation-engine")
include(":timeline-engine")
include(":rigging-engine")
include(":drawing-tools")
include(":layers-engine")
include(":audio-engine")
include(":camera-engine")
include(":effects-engine")
include(":rendering-engine")
include(":project-manager")
include(":import-export")
include(":file-format")
include(":settings")
