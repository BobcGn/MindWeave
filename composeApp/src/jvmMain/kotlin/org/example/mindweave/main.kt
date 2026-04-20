package org.example.mindweave

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.example.mindweave.platform.PlatformContext

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "思织",
    ) {
        App(
            platformContext = PlatformContext(),
            aiSettings = desktopAiSettings(),
        )
    }
}
