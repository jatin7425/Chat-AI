package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_config")
data class UserConfigEntity(
    @PrimaryKey val id: Int = 1,
    val darkTheme: Boolean = true,
    val firebaseUid: String? = null,
    val firebaseEmail: String? = null,
    val spacesApiBaseUrl: String = ""
)
