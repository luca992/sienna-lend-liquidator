pluginManagement {
    repositories {
        gradlePluginPortal()
    }

    plugins {
        id("de.fayard.refreshVersions") version "0.60.3"
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

plugins {
    id("de.fayard.refreshVersions")
}


rootProject.name = "sienna-lend-liquidator"
include(":app")
