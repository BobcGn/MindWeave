plugins {
    id("org.jetbrains.kotlin.multiplatform") apply false
    id("org.jetbrains.kotlin.plugin.serialization") apply false
    id("app.cash.sqldelight") apply false
}

val ohosToolchain = providers.gradleProperty("mindweave.ohos.toolchain").orElse("standard")

tasks.register("harmonyBuildDoctor") {
    group = "harmony"
    description = "Print the active OHOS Gradle profile information."
    dependsOn(":shared:harmonyBuildDoctor")
}

tasks.register("prepareHarmonyDemoBridge") {
    group = "harmony"
    description = "Switch harmonyApp to the built-in demo bridge fallback."
    dependsOn(":shared:prepareHarmonyDemoBridge")
}

tasks.register("publishDebugBinariesToHarmonyApp") {
    group = "harmony"
    description = "Publish Debug native outputs for harmonyApp, or prepare the demo bridge fallback."
    dependsOn(":shared:publishDebugBinariesToHarmonyApp")
}

tasks.register("publishReleaseBinariesToHarmonyApp") {
    group = "harmony"
    description = "Publish Release native outputs for harmonyApp, or prepare the demo bridge fallback."
    dependsOn(":shared:publishReleaseBinariesToHarmonyApp")
}

tasks.register("printHarmonyToolchain") {
    group = "harmony"
    description = "Print the selected OHOS toolchain profile."
    doLast {
        println("mindweave.ohos.toolchain=${ohosToolchain.get()}")
    }
}
