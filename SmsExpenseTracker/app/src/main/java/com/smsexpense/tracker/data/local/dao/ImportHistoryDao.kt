package com.smsexpense.tracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.smsexpense.tracker.data.local.entity.ImportHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportHistoryDao {

    @Insert
    suspend fun insert(record: ImportHistoryEntity): Long

    @Query("SELECT * FROM import_history ORDER BY importedAt DESC")
    fun observeAll(): Flow<List<ImportHistoryEntity>>
}
