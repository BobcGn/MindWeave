import java.util.Locale
import org.gradle.api.GradleException
import org.gradle.api.tasks.Copy
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

fun String.capitalized(): String = replaceFirstChar {
    if (it.isLowerCase()) {
        it.titlecase(Locale.US)
    } else {
        it.toString()
    }
}

val ohosToolchain = providers.gradleProperty("mindweave.ohos.toolchain").orElse("standard")
val useKbaToolchain = ohosToolchain.get().equals("kba", ignoreCase = true)

fun version(name: String, standard: String, kba: String = standard): String =
    providers.gradleProperty(name).orElse(if (useKbaToolchain) kba else standard).get()

val coroutinesVersion = version(
    name = "mindweave.ohos.coroutinesVersion",
    standard = "1.10.2",
    kba = "1.8.0-KBA-001",
)
val datetimeVersion = version(
    name = "mindweave.ohos.datetimeVersion",
    standard = "0.7.1",
    kba = "0.6.2",
)
val serializationVersion = version(
    name = "mindweave.ohos.serializationVersion",
    standard = "1.9.0",
    kba = "1.9.0",
)
val ktorVersion = version(
    name = "mindweave.ohos.ktorVersion",
    standard = "3.4.1",
)

val harmonyLibDir = rootProject.layout.projectDirectory.dir("harmonyApp/entry/src/main/libs/arm64-v8a")
val harmonyLibFile = harmonyLibDir.file("libmindweave.so")
val harmonyModeFile = harmonyLibDir.file("mindweave_bridge_mode.txt")

var detectedPresetName: String? = null
var hasOhosTarget = false

@Suppress("DEPRECATION", "UNCHECKED_CAST")
fun KotlinMultiplatformExtension.createOhosTargetOrNull(targetName: String): KotlinNativeTarget? {
    val targetMethod = listOf("ohosArm64", "harmonyOSArm64")
        .firstNotNullOfOrNull { methodName ->
            val stringOverload = javaClass.methods.firstOrNull { method ->
                method.name == methodName &&
                    method.parameterCount == 1 &&
                    method.parameterTypes[0] == String::class.java
            }
            if (stringOverload != null) {
                detectedPresetName = methodName
                return@firstNotNullOfOrNull stringOverload
            }

            val noArgOverload = javaClass.methods.firstOrNull { method ->
                method.name == methodName && method.parameterCount == 0
            }
            if (noArgOverload != null) {
                detectedPresetName = methodName
            }
            noArgOverload
        }
        ?: return null

    return when (targetMethod.parameterCount) {
        1 -> targetMethod.invoke(this, targetName) as? KotlinNativeTarget
        0 -> targetMethod.invoke(this) as? KotlinNativeTarget
        else -> null
    }
}

kotlin {
    val ohosTarget = createOhosTargetOrNull("ohosArm64")
    hasOhosTarget = ohosTarget != null

    if (ohosTarget != null) {
        ohosTarget.binaries {
            sharedLib {
                baseName = "mindweave"
            }
        }
    } else {
        // Keep the profile configurable even without an OHOS-native toolchain.
        jvm("harmonyFallback")
    }

    sourceSets {
        val commonMain = getByName("commonMain")
        commonMain.kotlin.srcDir("src/ohosProfileCommonMain/kotlin")
        commonMain.kotlin.exclude("org/example/mindweave/data/local/SqlDelightRepositories.kt")
        commonMain.kotlin.exclude("org/example/mindweave/data/local/SqlDelightProfileRepositories.kt")
        commonMain.kotlin.exclude("org/example/mindweave/database/DriverFactory.kt")
        commonMain.kotlin.exclude("org/example/mindweave/network/HttpClientFactory.kt")
        commonMain.kotlin.exclude("org/example/mindweave/network/KtorSyncApi.kt")
        commonMain.kotlin.exclude("org/example/mindweave/ai/OpenAiAgent.kt")
        commonMain.kotlin.exclude("org/example/mindweave/sync/LocalChangeApplier.kt")
        commonMain.kotlin.exclude("org/example/mindweave/util/TimeFormatting.kt")
        commonMain.kotlin.exclude("org/example/mindweave/util/AppJson.kt")
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        if (ohosTarget != null) {
            val ohosArm64Main = maybeCreate("ohosArm64Main")
            ohosArm64Main.kotlin.exclude("org/example/mindweave/database/DriverFactory.ohos.kt")
            ohosArm64Main.kotlin.exclude("org/example/mindweave/network/HttpClientFactory.ohos.kt")
            ohosArm64Main.dependencies {
            }
        }
    }
}

val prepareHarmonyDemoBridge = tasks.register("prepareHarmonyDemoBridge") {
    group = "harmony"
    description = "Fail because the Harmony demo bridge fallback has been removed."
    notCompatibleWithConfigurationCache("Fails explicitly because the demo bridge fallback is no longer supported.")

    doLast {
        throw GradleException(
            "Harmony demo bridge fallback has been removed. " +
                "Configure a real OHOS Kotlin/Native toolchain and run publishDebugBinariesToHarmonyApp.",
        )
    }
}

tasks.register("harmonyBuildDoctor") {
    group = "harmony"
    description = "Print the active OHOS Gradle profile and native bridge status."
    notCompatibleWithConfigurationCache("Prints diagnostics captured from Gradle script state.")

    doLast {
        println("mindweave.ohos.toolchain=${ohosToolchain.get()}")
        println("detected.kotlin.ohos.preset=${detectedPresetName ?: "none"}")
        println("native.target.available=$hasOhosTarget")
        println("published.lib.path=${harmonyLibFile.asFile.absolutePath}")
        println("published.lib.exists=${harmonyLibFile.asFile.exists()}")
        println("bridge.mode.file=${harmonyModeFile.asFile.absolutePath}")
        if (harmonyModeFile.asFile.exists()) {
            println("bridge.mode=${harmonyModeFile.asFile.readText().trim()}")
        }
        println("demo.bridge.supported=false")
        if (!hasOhosTarget) {
            println("No OHOS Kotlin preset is available in the active Gradle toolchain.")
            println("Use -Pmindweave.ohos.toolchain=kba to enable a real Harmony Kotlin/Native build.")
        }
    }
}

arrayOf("debug", "release").forEach { buildType ->
    val buildTypeName = buildType.capitalized()
    val linkTaskName = "link${buildTypeName}SharedOhosArm64"
    val publishTaskName = "publish${buildTypeName}BinariesToHarmonyApp"

    if (hasOhosTarget) {
        val sourceSo = layout.buildDirectory.file("bin/ohosArm64/${buildType}Shared/libmindweave.so")
        tasks.register<Copy>(publishTaskName) {
            group = "harmony"
            description = "Publish ${buildTypeName} libmindweave.so into harmonyApp."
            notCompatibleWithConfigurationCache("Publishes native outputs and writes bridge mode markers.")
            dependsOn(linkTaskName)
            from(sourceSo)
            into(harmonyLibDir)
            rename { "libmindweave.so" }
            outputs.file(harmonyLibFile)
            outputs.file(harmonyModeFile)

            doFirst {
                harmonyLibDir.asFile.mkdirs()
                harmonyModeFile.asFile.writeText("kotlin\n")
            }

            doLast {
                println("Synced libmindweave.so -> ${harmonyLibFile.asFile.absolutePath}")
                println("Bridge mode -> kotlin")
                println("Next: open harmonyApp in DevEco Studio and build the entry HAP.")
            }
        }
    } else {
        tasks.register(linkTaskName) {
            group = "harmony"
            description = "Fail because a real OHOS Kotlin target is required."
            notCompatibleWithConfigurationCache("Fails explicitly when no OHOS Kotlin target is available.")

            doLast {
                throw GradleException(
                    "OHOS Kotlin target is unavailable under the '${ohosToolchain.get()}' toolchain. " +
                        "A real Harmony bridge is required; no demo fallback is available.",
                )
            }
        }

        tasks.register(publishTaskName) {
            group = "harmony"
            description = "Publish real Harmony native outputs or fail if no OHOS target is available."
            notCompatibleWithConfigurationCache("Fails explicitly when no OHOS Kotlin target is available.")
            dependsOn(linkTaskName)
        }
    }
}
