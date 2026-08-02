package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_config")
data class UserConfigEntity(
    @PrimaryKey val id: Int = 1,
    val userName: String = "",
    val userBio: String = "",
    val baseUrl: String = "",
    val selectedModel: String = "nvidia",
    val darkTheme: Boolean = true,
    val isOnboardingCompleted: Boolean = false
)
