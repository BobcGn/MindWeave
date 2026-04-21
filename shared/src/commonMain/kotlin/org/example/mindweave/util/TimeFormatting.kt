package org.example.mindweave.util

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun formatMoment(epochMs: Long): String {
    val moment = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.currentSystemDefault())
    return buildString {
        append((moment.month.ordinal + 1).toString().padStart(2, '0'))
        append("-")
        append(moment.day.toString().padStart(2, '0'))
        append(" ")
        append(moment.hour.toString().padStart(2, '0'))
        append(":")
        append(moment.minute.toString().padStart(2, '0'))
    }
}
