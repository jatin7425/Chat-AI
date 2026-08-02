package com.example.data.dao

import androidx.room.*
import com.example.data.model.ChatMessageEntity
import com.example.data.model.ChatSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_sessions ORDER BY lastMessageTime DESC")
    fun getAllSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions ORDER BY lastMessageTime DESC")
    suspend fun getAllSessionsSync(): List<ChatSessionEntity>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    suspend fun getAllMessagesSync(): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_sessions WHERE personaId = :personaId LIMIT 1")
    suspend fun getSessionByPersonaId(personaId: Long): ChatSessionEntity?

    @Query("SELECT * FROM chat_sessions WHERE sessionId = :sessionId")
    suspend fun getSessionById(sessionId: String): ChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<ChatSessionEntity>)

    @Query("DELETE FROM chat_sessions")
    suspend fun deleteAllSessions()

    @Update
    suspend fun updateSession(session: ChatSessionEntity)

    @Query("DELETE FROM chat_sessions WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE personaId = :personaId ORDER BY timestamp ASC")
    fun getMessagesForPersona(personaId: Long): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAllMessages()

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String)

    @Query("DELETE FROM chat_messages WHERE personaId = :personaId")
    suspend fun deleteMessagesForPersona(personaId: Long)

    @Query("DELETE FROM chat_messages WHERE messageId = :messageId")
    suspend fun deleteMessageById(messageId: String)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId AND isError = 0 ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessagesForSessionSync(sessionId: String, limit: Int = 10): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_sessions WHERE sessionId LIKE 'dual_%' ORDER BY lastMessageTime DESC")
    suspend fun getAllDualSessionsSync(): List<ChatSessionEntity>

    @Query("SELECT * FROM chat_messages WHERE personaId = :personaId AND isError = 0 ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessagesForPersonaSync(personaId: Long, limit: Int = 10): List<ChatMessageEntity>
}
