pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Official Android AAR with ONNX Runtime linked privately on arm64/x86_64.
        ivy {
            name = "SherpaOnnxReleases"
            url = uri("https://github.com/k2-fsa/sherpa-onnx/releases/download")
            patternLayout { artifact("v[revision]/[artifact]-[revision].[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.k2fsa", "sherpa-onnx-static-link-onnxruntime") }
        }
    }
}

rootProject.name = "Viko"
include(":app")
