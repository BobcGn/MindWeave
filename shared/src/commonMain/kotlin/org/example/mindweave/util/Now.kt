package org.example.mindweave.util
import kotlin.time.Clock

fun currentEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()
