package org.example.mindweave.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.runTest
import org.example.mindweave.DEFAULT_BOOTSTRAP_PASSWORD
import org.example.mindweave.DEFAULT_BOOTSTRAP_USERNAME
import org.example.mindweave.data.local.SqlDelightAccountRepository
import org.example.mindweave.db.MindWeaveDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileRepositoryTest {
    @Test
    fun bootstrapAccountShouldRequireResetAndRecordSuccessfulLogin() = runTest {
        val repository = createRepository()

        repository.ensureDefaultAccount("user-1")
        val seeded = repository.getAccount("user-1")

        assertNotNull(seeded)
        assertEquals(DEFAULT_BOOTSTRAP_USERNAME, seeded.username)
        assertTrue(seeded.mustChangeCredentials)
        assertEquals(seeded.createdAtEpochMs, seeded.credentialsUpdatedAtEpochMs)
        assertNull(seeded.lastLoginAtEpochMs)

        val authenticated = repository.authenticate(DEFAULT_BOOTSTRAP_USERNAME, DEFAULT_BOOTSTRAP_PASSWORD)

        assertNotNull(authenticated)
        assertTrue(authenticated.mustChangeCredentials)
        assertNotNull(authenticated.lastLoginAtEpochMs)

        val reloaded = repository.getAccount("user-1")
        assertEquals(authenticated.lastLoginAtEpochMs, reloaded?.lastLoginAtEpochMs)
    }

    @Test
    fun bootstrapCredentialsShouldBeRejectedAfterInitialization() = runTest {
        val repository = createRepository()
        repository.ensureDefaultAccount("user-1")

        assertEquals(
            "默认账号不能继续使用，请更换为个人账号。",
            repository.forceResetCredentials("user-1", DEFAULT_BOOTSTRAP_USERNAME, "custom-pass"),
        )
        assertEquals(
            "默认密码不能继续使用，请设置新密码。",
            repository.forceResetCredentials("user-1", "custom-user", DEFAULT_BOOTSTRAP_PASSWORD),
        )

        assertNull(repository.forceResetCredentials("user-1", "custom-user", "custom-pass"))

        assertEquals(
            "默认账号不能继续使用，请更换为个人账号。",
            repository.changeCredentials("user-1", "custom-pass", DEFAULT_BOOTSTRAP_USERNAME, "next-pass"),
        )
        assertEquals(
            "默认密码不能继续使用，请设置新密码。",
            repository.changeCredentials("user-1", "custom-pass", "custom-user", DEFAULT_BOOTSTRAP_PASSWORD),
        )
    }

    @Test
    fun credentialResetShouldEnableCustomLoginAndUpdateMetadata() = runTest {
        val repository = createRepository()
        repository.ensureDefaultAccount("user-1")

        assertNull(repository.forceResetCredentials("user-1", "owner", "owner-pass"))
        assertNull(repository.authenticate(DEFAULT_BOOTSTRAP_USERNAME, DEFAULT_BOOTSTRAP_PASSWORD))

        val authenticated = repository.authenticate("owner", "owner-pass")
        val stored = repository.getAccount("user-1")

        assertNotNull(authenticated)
        assertNotNull(stored)
        assertFalse(stored.mustChangeCredentials)
        assertEquals("owner", stored.username)
        assertTrue(stored.credentialsUpdatedAtEpochMs > stored.createdAtEpochMs)
        assertNotNull(stored.lastLoginAtEpochMs)
    }

    private fun createRepository(): SqlDelightAccountRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MindWeaveDatabase.Schema.create(driver)

        var now = 1000L
        return SqlDelightAccountRepository(MindWeaveDatabase(driver)) {
            now.also { now += 100L }
        }
    }
}
