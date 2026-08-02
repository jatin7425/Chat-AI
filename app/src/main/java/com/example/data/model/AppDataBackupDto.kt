package com.example.data.model

data class AppDataBackupDto(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val userConfig: UserConfigEntity? = null,
    val personas: List<PersonaEntity> = emptyList(),
    val sessions: List<ChatSessionEntity> = emptyList(),
    val messages: List<ChatMessageEntity> = emptyList(),
    val memoryRecaps: List<MemoryRecapEntity> = emptyList()
)
