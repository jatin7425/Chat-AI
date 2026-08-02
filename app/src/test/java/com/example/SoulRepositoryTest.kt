package com.example

import com.example.data.dao.UserConfigDao
import com.example.data.model.UserConfigEntity
import com.example.data.repository.SoulRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SoulRepositoryTest {

    private lateinit var userConfigDao: FakeUserConfigDao
    private lateinit var repository: SoulRepository

    @Before
    fun setup() {
        userConfigDao = FakeUserConfigDao()
        repository = SoulRepository(userConfigDao)
    }

    @Test
    fun `getUserConfig creates and persists a default config when none exists`() = runTest {
        val config = repository.getUserConfig()

        assertEquals(1, config.id)
        assertEquals(userConfigDao.saved, config)
    }

    @Test
    fun `saveUserConfig persists the given config`() = runTest {
        val config = UserConfigEntity(id = 1, darkTheme = false, spacesApiBaseUrl = "https://example.com")

        repository.saveUserConfig(config)

        assertEquals(config, userConfigDao.saved)
    }

    @Test
    fun `updateFirebaseIdentity forwards uid and email to the dao`() = runTest {
        repository.updateFirebaseIdentity("uid-123", "user@example.com")

        assertEquals("uid-123", userConfigDao.lastUid)
        assertEquals("user@example.com", userConfigDao.lastEmail)
    }

    @Test
    fun `updateSpacesApiBaseUrl forwards the url to the dao`() = runTest {
        repository.updateSpacesApiBaseUrl("https://tunnel.example.com")

        assertEquals("https://tunnel.example.com", userConfigDao.lastBaseUrl)
    }
}

class FakeUserConfigDao : UserConfigDao {
    var saved: UserConfigEntity? = null
    var lastUid: String? = null
    var lastEmail: String? = null
    var lastBaseUrl: String? = null

    override fun getUserConfigFlow(): Flow<UserConfigEntity?> = flowOf(saved)
    override suspend fun getUserConfig(): UserConfigEntity? = saved
    override suspend fun insertOrUpdateConfig(config: UserConfigEntity) {
        saved = config
    }
    override suspend fun updateFirebaseIdentity(uid: String?, email: String?) {
        lastUid = uid
        lastEmail = email
    }
    override suspend fun updateSpacesApiBaseUrl(url: String) {
        lastBaseUrl = url
    }
}
