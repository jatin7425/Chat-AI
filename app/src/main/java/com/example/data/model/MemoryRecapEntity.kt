package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memory_recaps")
data class MemoryRecapEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personaId: Long,
    val title: String,
    val summary: String,
    val sentimentTag: String = "Warmth",
    val bondLevel: Int = 1,
    val keyMomentsJson: String = "[]",
    val timestamp: Long = System.currentTimeMillis()
)

data class MemoryMoment(
    val dateStr: String,
    val title: String,
    val excerpt: String,
    val emotion: String,
    val isMilestone: Boolean = false
)
