rootProject.name = "MindWeave"
rootProject.buildFileName = "build.2.0.ohos.gradle.kts"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    val ohosToolchain = (gradle.startParameter.projectProperties["mindweave.ohos.toolchain"] ?: "standard")
        .lowercase()
    val kotlinPluginVersion = gradle.startParameter.projectProperties["mindweave.ohos.kotlinVersion"]
        ?: if (ohosToolchain == "kba") "2.0.21-KBA-005" else "2.3.20"
    val sqlDelightPluginVersion = gradle.startParameter.projectProperties["mindweave.ohos.sqldelightVersion"]
        ?: "2.1.0"
    val additionalOhosRepo = gradle.startParameter.projectProperties["mindweave.ohos.repo"]

    repositories {
        mavenLocal()
        if (ohosToolchain == "kba") {
            maven("https://mirrors.tencent.com/nexus/repository/maven-tencent")
            maven("https://mirrors.tencent.com/nexus/repository/maven-public")
        }
        if (!additionalOhosRepo.isNullOrBlank()) {
            maven(additionalOhosRepo)
        }
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("org.jetbrains.kotlin.multiplatform") version kotlinPluginVersion
        id("org.jetbrains.kotlin.plugin.serialization") version kotlinPluginVersion
        id("app.cash.sqldelight") version sqlDelightPluginVersion
    }
}

dependencyResolutionManagement {
    val ohosToolchain = (gradle.startParameter.projectProperties["mindweave.ohos.toolchain"] ?: "standard")
        .lowercase()
    val additionalOhosRepo = gradle.startParameter.projectProperties["mindweave.ohos.repo"]

    repositories {
        mavenLocal()
        if (ohosToolchain == "kba") {
            maven("https://mirrors.tencent.com/nexus/repository/maven-tencent")
            maven("https://mirrors.tencent.com/nexus/repository/maven-public")
        }
        if (!additionalOhosRepo.isNullOrBlank()) {
            maven(additionalOhosRepo)
        }
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

val moduleBuildFileName = "build.2.0.ohos.gradle.kts"

include(":shared")
project(":shared").buildFileName = moduleBuildFileName
