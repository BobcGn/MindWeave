package org.example.mindweave.repository

import kotlinx.coroutines.flow.Flow
import org.example.mindweave.domain.model.UserAccount

interface AccountRepository {
    fun observeAccount(userId: String): Flow<UserAccount?>

    suspend fun getAccount(userId: String): UserAccount?

    suspend fun authenticate(userId: String, username: String, password: String): UserAccount?

    suspend fun ensureDefaultAccount(userId: String)

    suspend fun forceResetCredentials(
        userId: String,
        newUsername: String,
        newPassword: String,
    ): String?

    suspend fun changeCredentials(
        userId: String,
        currentPassword: String,
        newUsername: String,
        newPassword: String,
    ): String?
}
