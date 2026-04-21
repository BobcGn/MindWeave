package org.example.mindweave

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.example.mindweave.ai.AiSettings
import org.example.mindweave.platform.PlatformContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App(
                platformContext = PlatformContext(applicationContext),
                aiSettings = AiSettings.Disabled,
            )
        }
    }
}
