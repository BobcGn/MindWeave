package org.example.mindweave.platform

import android.content.Context

actual class PlatformContext(
    val context: Context,
    val databaseName: String = "mindweave.db",
)
