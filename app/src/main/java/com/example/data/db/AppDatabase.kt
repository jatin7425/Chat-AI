package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.ChatDao
import com.example.data.dao.MemoryRecapDao
import com.example.data.dao.PersonaDao
import com.example.data.dao.UserConfigDao
import com.example.data.model.ChatMessageEntity
import com.example.data.model.ChatSessionEntity
import com.example.data.model.MemoryRecapEntity
import com.example.data.model.PersonaEntity
import com.example.data.model.UserConfigEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import androidx.room.migration.Migration

@Database(
    entities = [
        PersonaEntity::class,
        UserConfigEntity::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        MemoryRecapEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun personaDao(): PersonaDao
    abstract fun userConfigDao(): UserConfigDao
    abstract fun chatDao(): ChatDao
    abstract fun memoryRecapDao(): MemoryRecapDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE personas ADD COLUMN voice TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE personas ADD COLUMN avatarBlob BLOB DEFAULT NULL")
                db.execSQL("ALTER TABLE personas ADD COLUMN customChatBgBlob BLOB DEFAULT NULL")
                db.execSQL("ALTER TABLE personas ADD COLUMN customChatBgOpacity REAL NOT NULL DEFAULT 0.8")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "soul_ai_database"
                )
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigrationOnDowngrade()
                .addCallback(DatabaseCallback())
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        seedDatabase(database)
                    }
                }
            }
        }

        private suspend fun seedDatabase(db: AppDatabase) {
            val userConfigDao = db.userConfigDao()

            // Initialize clean dynamic user configuration
            if (userConfigDao.getUserConfig() == null) {
                userConfigDao.insertOrUpdateConfig(
                    UserConfigEntity(
                        id = 1,
                        userName = "",
                        userBio = "",
                        baseUrl = "",
                        selectedModel = "",
                        darkTheme = true,
                        isOnboardingCompleted = false
                    )
                )
            }
        }
    }
}
