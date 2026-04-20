package org.example.mindweave

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform