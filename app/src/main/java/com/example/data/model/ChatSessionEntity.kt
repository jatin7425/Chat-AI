package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val sessionId: String = UUID.randomUUID().toString(),
    val personaId: Long,
    val title: String,
    val lastMessage: String = "",
    val lastMessageTime: Long = System.currentTimeMillis()
)
