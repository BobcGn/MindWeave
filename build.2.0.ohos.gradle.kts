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
    description = "Fail because the Harmony demo bridge fallback has been removed."
    dependsOn(":shared:prepareHarmonyDemoBridge")
}

tasks.register("publishDebugBinariesToHarmonyApp") {
    group = "harmony"
    description = "Publish Debug real native outputs for harmonyApp."
    dependsOn(":shared:publishDebugBinariesToHarmonyApp")
}

tasks.register("publishReleaseBinariesToHarmonyApp") {
    group = "harmony"
    description = "Publish Release real native outputs for harmonyApp."
    dependsOn(":shared:publishReleaseBinariesToHarmonyApp")
}

tasks.register("printHarmonyToolchain") {
    group = "harmony"
    description = "Print the selected OHOS toolchain profile."
    doLast {
        println("mindweave.ohos.toolchain=${ohosToolchain.get()}")
    }
}
