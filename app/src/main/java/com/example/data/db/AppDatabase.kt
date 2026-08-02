package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.UserConfigDao
import com.example.data.model.UserConfigEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import androidx.room.migration.Migration

@Database(
    entities = [
        UserConfigEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userConfigDao(): UserConfigDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE personas ADD COLUMN voice TEXT NOT NULL DEFAULT ''")
            }
        }

        // TTS feature removed; drop the now-unused `voice` column (SQLite pre-3.35 needs a table
        // rebuild rather than ALTER TABLE ... DROP COLUMN for broad device compatibility).
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE personas_new (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        age INTEGER NOT NULL,
                        gender TEXT NOT NULL,
                        relationship TEXT NOT NULL,
                        avatarStyle TEXT NOT NULL,
                        avatarSeed TEXT NOT NULL,
                        avatarUri TEXT,
                        avatarBlob BLOB,
                        traits TEXT NOT NULL,
                        eyeColor TEXT NOT NULL,
                        hairStyle TEXT NOT NULL,
                        height TEXT NOT NULL,
                        build TEXT NOT NULL,
                        clothingStyle TEXT NOT NULL,
                        keyFeatures TEXT NOT NULL,
                        bio TEXT NOT NULL,
                        customChatBgUri TEXT,
                        customChatBgBlob BLOB,
                        customChatBgOpacity REAL NOT NULL,
                        emotionsJson TEXT NOT NULL,
                        isDefault INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO personas_new (
                        id, name, age, gender, relationship, avatarStyle, avatarSeed, avatarUri, avatarBlob,
                        traits, eyeColor, hairStyle, height, build, clothingStyle, keyFeatures, bio,
                        customChatBgUri, customChatBgBlob, customChatBgOpacity, emotionsJson, isDefault, createdAt
                    )
                    SELECT
                        id, name, age, gender, relationship, avatarStyle, avatarSeed, avatarUri, avatarBlob,
                        traits, eyeColor, hairStyle, height, build, clothingStyle, keyFeatures, bio,
                        customChatBgUri, customChatBgBlob, customChatBgOpacity, emotionsJson, isDefault, createdAt
                    FROM personas
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE personas")
                db.execSQL("ALTER TABLE personas_new RENAME TO personas")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_config ADD COLUMN firebaseUid TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE user_config ADD COLUMN firebaseEmail TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE user_config ADD COLUMN spacesApiBaseUrl TEXT NOT NULL DEFAULT ''")
            }
        }

        // Legacy single-persona chat feature removed entirely -- the new Firestore-backed
        // Spaces feature supersedes it. Drop its tables and slim user_config down to what's
        // still needed (the LLM/onboarding fields it used to carry are gone with it).
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS personas")
                db.execSQL("DROP TABLE IF EXISTS chat_sessions")
                db.execSQL("DROP TABLE IF EXISTS chat_messages")
                db.execSQL("DROP TABLE IF EXISTS memory_recaps")

                db.execSQL(
                    """
                    CREATE TABLE user_config_new (
                        id INTEGER NOT NULL PRIMARY KEY,
                        darkTheme INTEGER NOT NULL,
                        firebaseUid TEXT,
                        firebaseEmail TEXT,
                        spacesApiBaseUrl TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO user_config_new (id, darkTheme, firebaseUid, firebaseEmail, spacesApiBaseUrl)
                    SELECT id, darkTheme, firebaseUid, firebaseEmail, spacesApiBaseUrl FROM user_config
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE user_config")
                db.execSQL("ALTER TABLE user_config_new RENAME TO user_config")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_config ADD COLUMN mcpServerUrl TEXT NOT NULL DEFAULT ''")
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
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
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

            if (userConfigDao.getUserConfig() == null) {
                userConfigDao.insertOrUpdateConfig(
                    UserConfigEntity(id = 1, darkTheme = true)
                )
            }
        }
    }
}
