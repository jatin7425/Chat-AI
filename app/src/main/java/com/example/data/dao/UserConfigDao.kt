package com.example.data.dao

import androidx.room.*
import com.example.data.model.UserConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserConfigDao {
    @Query("SELECT * FROM user_config WHERE id = 1")
    fun getUserConfigFlow(): Flow<UserConfigEntity?>

    @Query("SELECT * FROM user_config WHERE id = 1")
    suspend fun getUserConfig(): UserConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateConfig(config: UserConfigEntity)

    @Query("UPDATE user_config SET firebaseUid = :uid, firebaseEmail = :email WHERE id = 1")
    suspend fun updateFirebaseIdentity(uid: String?, email: String?)

    @Query("UPDATE user_config SET spacesApiBaseUrl = :url WHERE id = 1")
    suspend fun updateSpacesApiBaseUrl(url: String)
}
