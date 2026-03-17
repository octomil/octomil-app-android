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

// Workspace root (parent of octomil-app-android)
val workspaceRoot = settings.settingsDir.parentFile.absolutePath

// TRANSITIONAL: llama.cpp composite build needed because Gradle composite builds
// are not transitive. The SDK declares implementation("com.arm.aichat:lib") but
// Gradle can only substitute it if this root also includes the build.
// Removed when SDK publishes Maven AARs with bundled native libs.
includeBuild("$workspaceRoot/research/engines/llama.cpp/examples/llama.android") {
    dependencySubstitution {
        substitute(module("com.arm.aichat:lib")).using(project(":lib"))
    }
}

// sherpa-onnx Android lib for streaming speech-to-text (used by Octomil SDK)
includeBuild("$workspaceRoot/research/engines/sherpa-onnx/android/SherpaOnnxAar") {
    dependencySubstitution {
        substitute(module("com.k2fsa.sherpa:onnx")).using(project(":sherpa_onnx"))
    }
}

// whisper.cpp Android lib for batch speech-to-text
includeBuild("$workspaceRoot/research/engines/whisper.cpp/examples/whisper.android") {
    dependencySubstitution {
        substitute(module("com.whispercpp:lib")).using(project(":lib"))
    }
}
