plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    val ohosTarget = ohosArm64()
    ohosTarget.binaries {
        sharedLib {
            baseName = "mindweave"
            linkerOpts("-lsqlite3")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.clientCore)
            implementation(libs.ktor.clientContentNegotiation)
            implementation(libs.ktor.serializationKotlinxJson)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        val ohosArm64Main by getting {
            dependencies {
                implementation(libs.sqldelight.nativeDriver)
                implementation(libs.ktor.clientMock)
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
