package com.example

import com.example.data.dao.ChatDao
import com.example.data.dao.MemoryRecapDao
import com.example.data.dao.PersonaDao
import com.example.data.dao.UserConfigDao
import com.example.data.model.ChatMessageEntity
import com.example.data.model.ChatSessionEntity
import com.example.data.model.MemoryRecapEntity
import com.example.data.model.PersonaEntity
import com.example.data.model.UserConfigEntity
import com.example.data.repository.SoulRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SoulRepositoryTest {

    private lateinit var personaDao: FakePersonaDao
    private lateinit var userConfigDao: FakeUserConfigDao
    private lateinit var chatDao: FakeChatDao
    private lateinit var memoryRecapDao: FakeMemoryRecapDao
    private lateinit var repository: SoulRepository

    @Before
    fun setup() {
        personaDao = FakePersonaDao()
        userConfigDao = FakeUserConfigDao()
        chatDao = FakeChatDao()
        memoryRecapDao = FakeMemoryRecapDao()
        repository = SoulRepository(personaDao, userConfigDao, chatDao, memoryRecapDao)
    }

    @Test
    fun `parseEmotions successfully parses valid JSON`() {
        val json = """[{"emotion": "Joy", "percentage": 80}, {"emotion": "Trust", "percentage": 75}]"""
        val emotions = repository.parseEmotions(json)
        
        assertEquals(2, emotions.size)
        assertEquals("Joy", emotions[0].emotion)
        assertEquals(80, emotions[0].percentage)
        assertEquals("Trust", emotions[1].emotion)
        assertEquals(75, emotions[1].percentage)
    }

    @Test
    fun `parseEmotions returns empty list for invalid JSON`() {
        val json = """invalid_json"""
        val emotions = repository.parseEmotions(json)
        
        assertTrue(emotions.isEmpty())
    }

    @Test
    fun `buildSystemPrompt includes memory recaps`() = runTest {
        val persona = PersonaEntity(
            id = 1L,
            name = "Alice",
            age = 25,
            gender = "Female",
            relationship = "Friend",
            traits = "Kind, Helpful",
            bio = "Loves nature",
            emotionsJson = """[{"emotion": "Joy", "percentage": 80}]"""
        )
        
        val userConfig = UserConfigEntity(
            userName = "Bob",
            userBio = "Loves tech"
        )
        
        memoryRecapDao.recaps = listOf(
            MemoryRecapEntity(personaId = 1L, title = "Recap", summary = "Bob mentioned he loves to code.", timestamp = 0L)
        )
        
        val prompt = repository.buildSystemPrompt(persona, userConfig, emptyList())
        
        assertTrue(prompt.contains("You are playing the role of Alice"))
        assertTrue(prompt.contains("Bob mentioned he loves to code."))
        assertTrue(prompt.contains("Joy (80%)"))
    }
}

class FakePersonaDao : PersonaDao {
    override fun getAllPersonas(): Flow<List<PersonaEntity>> = flowOf(emptyList())
    override suspend fun getAllPersonasSync(): List<PersonaEntity> = emptyList()
    override suspend fun getPersonaById(id: Long): PersonaEntity? = null
    override fun getPersonaByIdFlow(id: Long): Flow<PersonaEntity?> = flowOf(null)
    override suspend fun insertPersona(persona: PersonaEntity): Long = 1L
    override suspend fun updatePersona(persona: PersonaEntity) {}
    override suspend fun deletePersona(persona: PersonaEntity) {}
    override suspend fun insertPersonas(personas: List<PersonaEntity>) {}
    override suspend fun deleteAllPersonas() {}
    override suspend fun getPersonaCount(): Int = 0
}

class FakeUserConfigDao : UserConfigDao {
    override fun getUserConfigFlow(): Flow<UserConfigEntity?> = flowOf(null)
    override suspend fun getUserConfig(): UserConfigEntity? = null
    override suspend fun insertOrUpdateConfig(config: UserConfigEntity) {}
    override suspend fun updateFirebaseIdentity(uid: String?, email: String?) {}
    override suspend fun updateSpacesApiBaseUrl(url: String) {}
}

class FakeChatDao : ChatDao {
    override fun getAllSessions(): Flow<List<ChatSessionEntity>> = flowOf(emptyList())
    override suspend fun getSessionById(sessionId: String): ChatSessionEntity? = null
    override suspend fun getSessionByPersonaId(personaId: Long): ChatSessionEntity? = null
    override suspend fun insertSession(session: ChatSessionEntity) {}
    override suspend fun updateSession(session: ChatSessionEntity) {}
    override suspend fun deleteSession(sessionId: String) {}
    override fun getMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>> = flowOf(emptyList())
    override suspend fun insertMessage(message: ChatMessageEntity) {}
    override suspend fun deleteMessagesForSession(sessionId: String) {}
    override suspend fun deleteMessagesForPersona(personaId: Long) {}
    override suspend fun deleteMessageById(messageId: String) {}
    override suspend fun getRecentMessagesForSessionSync(sessionId: String, limit: Int): List<ChatMessageEntity> = emptyList()
    override suspend fun getAllDualSessionsSync(): List<ChatSessionEntity> = emptyList()
    override suspend fun getRecentMessagesForPersonaSync(personaId: Long, limit: Int): List<ChatMessageEntity> = emptyList()
    override suspend fun getAllSessionsSync(): List<ChatSessionEntity> = emptyList()
    override suspend fun getAllMessagesSync(): List<ChatMessageEntity> = emptyList()
    override suspend fun insertSessions(sessions: List<ChatSessionEntity>) {}
    override suspend fun deleteAllSessions() {}
    override fun getMessagesForPersona(personaId: Long): Flow<List<ChatMessageEntity>> = flowOf(emptyList())
    override suspend fun insertMessages(messages: List<ChatMessageEntity>) {}
    override suspend fun deleteAllMessages() {}
}

class FakeMemoryRecapDao : MemoryRecapDao {
    var recaps = emptyList<MemoryRecapEntity>()
    override fun getRecapsForPersona(personaId: Long): Flow<List<MemoryRecapEntity>> = flowOf(recaps)
    override suspend fun getRecapsForPersonaSync(personaId: Long): List<MemoryRecapEntity> = recaps
    override suspend fun insertRecap(recap: MemoryRecapEntity): Long = 1L
    override suspend fun deleteRecapsForPersona(personaId: Long) {}
    override suspend fun getLatestRecapForPersona(personaId: Long): MemoryRecapEntity? = null
    override suspend fun getAllRecapsSync(): List<MemoryRecapEntity> = emptyList()
    override suspend fun insertRecaps(recaps: List<MemoryRecapEntity>) {}
    override suspend fun deleteRecapById(id: Long) {}
    override suspend fun deleteAllRecaps() {}
}
