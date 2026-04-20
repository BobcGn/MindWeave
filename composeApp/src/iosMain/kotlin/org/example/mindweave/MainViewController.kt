package org.example.mindweave

import androidx.compose.ui.window.ComposeUIViewController
import org.example.mindweave.ai.AiSettings
import org.example.mindweave.platform.PlatformContext

fun MainViewController() = ComposeUIViewController {
    App(
        platformContext = PlatformContext(),
        aiSettings = AiSettings.Disabled,
    )
}
