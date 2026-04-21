package org.example.mindweave

private class HarmonyPlatform : Platform {
    override val name: String = "HarmonyOS"
}

actual fun getPlatform(): Platform = HarmonyPlatform()
