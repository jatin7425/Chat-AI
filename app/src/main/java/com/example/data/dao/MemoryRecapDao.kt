package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.MemoryRecapEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryRecapDao {
    @Query("SELECT * FROM memory_recaps WHERE personaId = :personaId ORDER BY timestamp DESC")
    fun getRecapsForPersona(personaId: Long): Flow<List<MemoryRecapEntity>>

    @Query("SELECT * FROM memory_recaps WHERE personaId = :personaId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestRecapForPersona(personaId: Long): MemoryRecapEntity?

    @Query("SELECT * FROM memory_recaps WHERE personaId = :personaId ORDER BY timestamp DESC")
    suspend fun getRecapsForPersonaSync(personaId: Long): List<MemoryRecapEntity>

    @Query("SELECT * FROM memory_recaps ORDER BY timestamp DESC")
    suspend fun getAllRecapsSync(): List<MemoryRecapEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecap(recap: MemoryRecapEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecaps(recaps: List<MemoryRecapEntity>)

    @Query("DELETE FROM memory_recaps WHERE id = :id")
    suspend fun deleteRecapById(id: Long)

    @Query("DELETE FROM memory_recaps WHERE personaId = :personaId")
    suspend fun deleteRecapsForPersona(personaId: Long)

    @Query("DELETE FROM memory_recaps")
    suspend fun deleteAllRecaps()
}
