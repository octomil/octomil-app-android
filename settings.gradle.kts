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

// Include llama.cpp Android lib for on-device LLM inference
includeBuild(settings.settingsDir.resolve("..").resolve("research/engines/llama.cpp/examples/llama.android").canonicalFile) {
    dependencySubstitution {
        substitute(module("com.arm.aichat:lib")).using(project(":lib"))
    }
}

// Include whisper.cpp Android lib for on-device speech-to-text
includeBuild(settings.settingsDir.resolve("..").resolve("research/engines/whisper.cpp/examples/whisper.android").canonicalFile) {
    dependencySubstitution {
        substitute(module("com.whispercpp:lib")).using(project(":lib"))
    }
}
