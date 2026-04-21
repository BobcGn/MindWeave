package org.example.mindweave

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import mindweave.composeapp.generated.resources.Res
import mindweave.composeapp.generated.resources.app_icon
import org.example.mindweave.platform.PlatformContext
import org.jetbrains.compose.resources.painterResource

fun main() = application {
    Window(
        icon = painterResource(Res.drawable.app_icon),
        onCloseRequest = ::exitApplication,
        title = "思织",
    ) {
        App(
            platformContext = PlatformContext(),
            aiSettings = desktopAiSettings(),
        )
    }
}
