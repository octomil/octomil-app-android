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

rootProject.name = "OctomilApp"
include(":app")

// Include SDK modules from sibling repo
includeBuild("octomil-android") {
    dependencySubstitution {
        substitute(module("ai.octomil:octomil-client")).using(project(":octomil"))
        substitute(module("ai.octomil:octomil-ui")).using(project(":octomil"))
    }
}
