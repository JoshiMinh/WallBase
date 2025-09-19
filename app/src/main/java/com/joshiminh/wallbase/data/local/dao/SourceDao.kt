package com.joshiminh.wallbase.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.joshiminh.wallbase.data.local.entity.SourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources ORDER BY provider_key, title")
    fun observeSources(): Flow<List<SourceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSources(sources: List<SourceEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSource(source: SourceEntity): Long

    @Update
    suspend fun updateSource(source: SourceEntity)

    @Query("UPDATE sources SET is_enabled = :enabled WHERE key = :key")
    suspend fun setSourceEnabled(key: String, enabled: Boolean)

    @Query("SELECT * FROM sources WHERE key = :key LIMIT 1")
    suspend fun getSourceByKey(key: String): SourceEntity?

    @Query("SELECT * FROM sources WHERE provider_key = :provider AND config = :config LIMIT 1")
    suspend fun findSourceByProviderAndConfig(provider: String, config: String): SourceEntity?

    @Query("DELETE FROM sources WHERE source_id = :id")
    suspend fun deleteSourceById(id: Long)
}
