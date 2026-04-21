import java.util.Locale
import org.gradle.api.tasks.Copy
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("app.cash.sqldelight")
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
    kba = "0.6.1-SNAPSHOT",
)
val serializationVersion = version(
    name = "mindweave.ohos.serializationVersion",
    standard = "1.9.0",
    kba = "1.7.3-SNAPSHOT",
)
val ktorVersion = version(
    name = "mindweave.ohos.ktorVersion",
    standard = "3.4.1",
)
val sqlDelightVersion = version(
    name = "mindweave.ohos.sqldelightVersion",
    standard = "2.1.0",
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
                linkerOpts("-lsqlite3")
            }
        }
    } else {
        // Keep the profile configurable even without an OHOS-native toolchain.
        jvm("harmonyFallback")
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:$datetimeVersion")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
            implementation("io.ktor:ktor-client-core:$ktorVersion")
            implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
            implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
            implementation("app.cash.sqldelight:runtime:$sqlDelightVersion")
            implementation("app.cash.sqldelight:coroutines-extensions:$sqlDelightVersion")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        if (ohosTarget != null) {
            val ohosArm64Main = maybeCreate("ohosArm64Main")
            ohosArm64Main.dependencies {
                implementation("app.cash.sqldelight:native-driver:$sqlDelightVersion")
                implementation("io.ktor:ktor-client-mock:$ktorVersion")
            }
        }
    }
}

sqldelight {
    databases {
        create("MindWeaveDatabase") {
            packageName.set("org.example.mindweave.db")
        }
    }
}

val prepareHarmonyDemoBridge = tasks.register("prepareHarmonyDemoBridge") {
    group = "harmony"
    description = "Switch harmonyApp to the built-in demo bridge fallback."
    notCompatibleWithConfigurationCache("Writes bridge mode markers into harmonyApp.")
    outputs.file(harmonyModeFile)

    doLast {
        harmonyLibDir.asFile.mkdirs()
        harmonyModeFile.asFile.writeText("demo\n")
        println("Prepared harmonyApp demo bridge fallback.")
        println("Next: open harmonyApp in DevEco Studio and build the entry HAP directly.")
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
        if (!hasOhosTarget) {
            println("No OHOS Kotlin preset is available in the active Gradle toolchain.")
            println("Use -Pmindweave.ohos.toolchain=kba to try a Tencent KBA toolchain,")
            println("or keep the default profile and run with the demo bridge fallback.")
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
            description = "Compatibility shim for environments without an OHOS Kotlin target."
            notCompatibleWithConfigurationCache("Prints fallback diagnostics from Gradle script state.")
            dependsOn(prepareHarmonyDemoBridge)

            doLast {
                println("OHOS Kotlin target is unavailable under the '${ohosToolchain.get()}' toolchain.")
                println("Skipped native Kotlin build and prepared the demo bridge fallback instead.")
            }
        }

        tasks.register(publishTaskName) {
            group = "harmony"
            description = "Prepare harmonyApp using the built-in demo bridge fallback."
            notCompatibleWithConfigurationCache("Prints fallback diagnostics from Gradle script state.")
            dependsOn(linkTaskName)

            doLast {
                println("No libmindweave.so was published because no OHOS Kotlin target is active.")
            }
        }
    }
}
