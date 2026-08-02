package com.example.data.repository

import com.example.data.dao.UserConfigDao
import com.example.data.model.UserConfigEntity
import kotlinx.coroutines.flow.Flow

class SoulRepository(
    private val userConfigDao: UserConfigDao
) {
    val userConfigFlow: Flow<UserConfigEntity?> = userConfigDao.getUserConfigFlow()

    suspend fun getUserConfig(): UserConfigEntity {
        return userConfigDao.getUserConfig() ?: UserConfigEntity().also {
            userConfigDao.insertOrUpdateConfig(it)
        }
    }

    suspend fun saveUserConfig(config: UserConfigEntity) {
        userConfigDao.insertOrUpdateConfig(config)
    }

    suspend fun updateFirebaseIdentity(uid: String?, email: String?) {
        userConfigDao.updateFirebaseIdentity(uid, email)
    }

    suspend fun updateSpacesApiBaseUrl(url: String) {
        userConfigDao.updateSpacesApiBaseUrl(url)
    }

    suspend fun updateMcpServerUrl(url: String) {
        userConfigDao.updateMcpServerUrl(url)
    }
}
