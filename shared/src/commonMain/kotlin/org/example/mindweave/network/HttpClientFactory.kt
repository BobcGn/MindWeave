package org.example.mindweave.network

import io.ktor.client.HttpClient

expect fun createMindWeaveHttpClient(): HttpClient
