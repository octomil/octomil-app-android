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

// sherpa-onnx Android lib for streaming speech-to-text (used by Octomil SDK)
includeBuild(workspaceRoot.resolve("research/engines/sherpa-onnx/android/SherpaOnnxAar")) {
    dependencySubstitution {
        substitute(module("com.k2fsa.sherpa:onnx")).using(project(":sherpa_onnx"))
    }
}
