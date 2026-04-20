package org.example.mindweave.util

import kotlin.random.Random

object IdGenerator {
    fun next(prefix: String, now: Long = currentEpochMillis()): String {
        val random = buildString {
            repeat(10) {
                append(ALPHABET[Random.nextInt(ALPHABET.length)])
            }
        }
        return "$prefix-$now-$random"
    }

    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
}
