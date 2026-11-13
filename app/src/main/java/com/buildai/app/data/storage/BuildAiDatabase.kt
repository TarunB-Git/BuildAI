package com.buildai.app.data.storage

import android.content.Context
import androidx.room.*
import com.buildai.app.runtime.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "projects")
data class StoredProject(@PrimaryKey val id: String, val json: String)

@Entity(tableName = "agent_jobs", indices = [Index("projectId")])
data class StoredAgentJob(
    @PrimaryKey val id: String,
    val projectId: String,
    val kind: String,
    val status: String,
    val inputJson: String,
    val stage: String,
    val checkpoint: String = "",
    val error: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "''") val progressJson: String = ""
)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects WHERE id = :id") fun load(id: String): StoredProject?
    @Query("SELECT * FROM projects") fun observe(): Flow<List<StoredProject>>
    @Query("SELECT * FROM projects") fun list(): List<StoredProject>
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun save(project: StoredProject)
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun importOriginal(project: StoredProject)
    @Query("DELETE FROM projects WHERE id = :id") fun delete(id: String)
}

@Dao
interface AgentJobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun save(job: StoredAgentJob)
    @Query("SELECT * FROM agent_jobs WHERE id = :id") fun load(id: String): StoredAgentJob?
    @Query("SELECT * FROM agent_jobs ORDER BY updatedAt DESC") fun list(): List<StoredAgentJob>
    @Query("SELECT * FROM agent_jobs ORDER BY updatedAt DESC") fun observe(): Flow<List<StoredAgentJob>>
    @Query("UPDATE agent_jobs SET status = 'INTERRUPTED', error = 'Execution was interrupted. Retry from the saved input.', updatedAt = :now WHERE status IN ('RUNNING', 'QUEUED')")
    fun recoverInterrupted(now: Long)
    @Query("DELETE FROM agent_jobs WHERE projectId = :id") fun deleteForProject(id: String)
}

@Database(entities = [StoredProject::class, StoredAgentJob::class, NativeAppHead::class, ProjectRevision::class, AppRecord::class], version = 3, exportSchema = true)
abstract class BuildAiDatabase : RoomDatabase() {
    abstract fun projects(): ProjectDao
    abstract fun jobs(): AgentJobDao
    abstract fun nativeApps(): NativeAppDao

    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE agent_jobs ADD COLUMN progressJson TEXT NOT NULL DEFAULT ''")
            }
        }
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS native_app_heads (appId TEXT NOT NULL PRIMARY KEY, revisionId TEXT NOT NULL, runtimeJson TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS app_revisions (id TEXT NOT NULL PRIMARY KEY, appId TEXT NOT NULL, parentId TEXT, specJson TEXT NOT NULL, summary TEXT NOT NULL, verificationJson TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_app_revisions_appId ON app_revisions(appId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS app_records (appId TEXT NOT NULL, entityId TEXT NOT NULL, id TEXT NOT NULL, json TEXT NOT NULL, deleted INTEGER NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(appId, entityId, id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_app_records_appId ON app_records(appId)")
            }
        }
        @Volatile private var instance: BuildAiDatabase? = null
        fun get(context: Context): BuildAiDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, BuildAiDatabase::class.java, "buildai.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .enableMultiInstanceInvalidation()
                // The legacy synchronous API is retained during Stage 1. Move UI reads to
                // Flow/IO before increasing datasets; never use destructive migration.
                .allowMainThreadQueries()
                .build().also { instance = it }
        }
    }
}
