package org.example.mindweave.util

import kotlinx.serialization.json.Json

val MindWeaveJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    classDiscriminator = "kind"
}
