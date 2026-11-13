package com.buildai.app

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.buildai.app.data.storage.BuildAiDatabase
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class StorageMigrationTest {
    @Test fun progressMigrationRetainsProjectsInputsAndCheckpoints() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-fixture-${UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name).also { it.parentFile!!.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            // Independently construct the published schema 1, not schema 2 entities.
            old.execSQL("CREATE TABLE projects (id TEXT NOT NULL PRIMARY KEY, json TEXT NOT NULL)")
            old.execSQL("CREATE TABLE agent_jobs (id TEXT NOT NULL PRIMARY KEY, projectId TEXT NOT NULL, kind TEXT NOT NULL, status TEXT NOT NULL, inputJson TEXT NOT NULL, stage TEXT NOT NULL, checkpoint TEXT NOT NULL, error TEXT, updatedAt INTEGER NOT NULL)")
            old.execSQL("CREATE INDEX index_agent_jobs_projectId ON agent_jobs(projectId)")
            old.execSQL("INSERT INTO projects VALUES (?, ?)", arrayOf("saved-project", "{\"draftText\":\"Retained draft\"}"))
            old.execSQL("INSERT INTO agent_jobs VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", arrayOf("saved-job", "saved-project", "BUILD", "INTERRUPTED", "immutable-input", "Last saved feature", "completed-checkpoint", "Interrupted", 100L))
            old.version = 1
        }
        val db = Room.databaseBuilder(context, BuildAiDatabase::class.java, name)
            .addMigrations(BuildAiDatabase.MIGRATION_1_2, BuildAiDatabase.MIGRATION_2_3).allowMainThreadQueries().build()
        try {
            assertTrue(db.projects().load("saved-project")!!.json.contains("Retained draft"))
            val job = checkNotNull(db.jobs().load("saved-job"))
            assertEquals("immutable-input", job.inputJson)
            assertEquals("completed-checkpoint", job.checkpoint)
            assertEquals("INTERRUPTED", job.status)
            assertEquals("", job.progressJson)
            db.jobs().save(job.copy(progressJson = "{\"completed\":7}"))
            assertEquals("{\"completed\":7}", db.jobs().load(job.id)!!.progressJson)
            assertEquals("immutable-input", db.jobs().load(job.id)!!.inputJson)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
