package com.animationstudio.projectmanager

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "project_metadata")
data class ProjectMetadataEntity(
    @PrimaryKey val id: String,
    val name: String,
    val updatedAt: Long,
    val width: Int,
    val height: Int,
    val fps: Int,
    val durationFrames: Int,
    val autosavePath: String,
    val externalUri: String? = null,
)

@Dao
interface ProjectMetadataDao {
    @Query("SELECT * FROM project_metadata ORDER BY updatedAt DESC")
    fun observeRecent(): Flow<List<ProjectMetadataEntity>>

    @Query("SELECT * FROM project_metadata WHERE id = :id")
    suspend fun get(id: String): ProjectMetadataEntity?

    @Upsert
    suspend fun upsert(metadata: ProjectMetadataEntity)

    @Query("DELETE FROM project_metadata WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(entities = [ProjectMetadataEntity::class], version = 1, exportSchema = false)
abstract class ProjectDatabase : RoomDatabase() {
    abstract fun metadata(): ProjectMetadataDao

    companion object {
        fun create(context: android.content.Context): ProjectDatabase = Room.databaseBuilder(
            context.applicationContext, ProjectDatabase::class.java, "animation-studio.db",
        ).fallbackToDestructiveMigration().build()
    }
}
