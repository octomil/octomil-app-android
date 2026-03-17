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
        mavenLocal() // For local engine AAR testing before Maven Central publish
    }
}

rootProject.name = "OctomilApp"
include(":app")

// Include SDK from sibling repo for local development.
// In production, consumers use: implementation("ai.octomil:octomil-client:1.0.0")
// Engine runtimes (llama.cpp, sherpa-onnx) arrive transitively through the SDK.
// For local dev: publish engines to mavenLocal first, then build the app.
includeBuild("octomil-android") {
    dependencySubstitution {
        substitute(module("ai.octomil:octomil-client")).using(project(":octomil"))
        substitute(module("ai.octomil:octomil-ui")).using(project(":octomil"))
    }
}
