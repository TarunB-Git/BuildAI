package com.buildai.app.runtime

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "native_app_heads")
data class NativeAppHead(@PrimaryKey val appId: String, val revisionId: String, val runtimeJson: String = "{}")
@Entity(tableName = "app_revisions", indices = [Index("appId")])
data class ProjectRevision(@PrimaryKey val id: String, val appId: String, val parentId: String?,
    val specJson: String, val summary: String, val verificationJson: String, val createdAt: Long)
@Entity(tableName = "app_records", primaryKeys = ["appId", "entityId", "id"], indices = [Index("appId")])
data class AppRecord(val appId: String, val entityId: String, val id: String, val json: String, val deleted: Boolean = false, val createdAt: Long = System.currentTimeMillis())
@Dao
interface NativeAppDao {
    @Query("SELECT * FROM native_app_heads WHERE appId = :id") fun head(id: String): NativeAppHead?
    @Query("SELECT * FROM native_app_heads") fun observeHeads(): Flow<List<NativeAppHead>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun saveHead(head: NativeAppHead)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun addRevision(revision: ProjectRevision)
    @Query("SELECT * FROM app_revisions WHERE id = :id") fun revision(id: String): ProjectRevision?
    @Query("SELECT * FROM app_revisions WHERE appId = :id ORDER BY createdAt DESC") fun history(id: String): List<ProjectRevision>
    @Query("SELECT * FROM app_records WHERE appId = :id AND deleted = 0 ORDER BY createdAt, id") fun records(id: String): List<AppRecord>
    @Query("SELECT * FROM app_records WHERE appId = :id AND deleted = 0 ORDER BY createdAt, id") fun observeRecords(id: String): Flow<List<AppRecord>>
    @Query("SELECT * FROM app_records WHERE appId = :id") fun allRecords(id: String): List<AppRecord>
    @Query("SELECT * FROM app_records WHERE appId = :appId AND entityId = :entityId AND id = :id") fun record(appId: String, entityId: String, id: String): AppRecord?
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun saveRecord(record: AppRecord)
    @Query("DELETE FROM app_records WHERE appId = :id") fun deleteRecords(id: String)
    @Query("DELETE FROM native_app_heads WHERE appId = :id") fun deleteHead(id: String)
    @Query("DELETE FROM app_revisions WHERE appId = :id") fun deleteRevisions(id: String)
}
