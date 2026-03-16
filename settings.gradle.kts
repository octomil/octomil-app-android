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
val workspaceRoot = settings.settingsDir.parentFile

// Include llama.cpp Android lib for on-device LLM inference
includeBuild(workspaceRoot.resolve("research/engines/llama.cpp/examples/llama.android")) {
    dependencySubstitution {
        substitute(module("com.arm.aichat:lib")).using(project(":lib"))
    }
}

// whisper.cpp removed — speech-to-text now provided by Octomil SDK
// via sherpa-onnx streaming recognizer (see octomil-android/settings.gradle.kts)
