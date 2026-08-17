package com.smsexpense.tracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.smsexpense.tracker.data.local.entity.PayerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PayerDao {

    @Insert
    suspend fun insert(payer: PayerEntity): Long

    @Update
    suspend fun update(payer: PayerEntity)

    @Query("DELETE FROM payers WHERE id = :id AND isSelf = 0")
    suspend fun deleteIfNotSelf(id: Long)

    @Query("SELECT * FROM payers ORDER BY isSelf DESC, sortOrder ASC, createdAt ASC")
    fun observeAll(): Flow<List<PayerEntity>>

    @Query("SELECT * FROM payers ORDER BY isSelf DESC, sortOrder ASC, createdAt ASC")
    suspend fun getAll(): List<PayerEntity>

    @Query("SELECT * FROM payers WHERE id = :id")
    suspend fun getById(id: Long): PayerEntity?

    @Query("SELECT * FROM payers WHERE isSelf = 1 LIMIT 1")
    suspend fun getSelf(): PayerEntity?

    @Query("SELECT COUNT(*) FROM payers")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM payers")
    suspend fun maxSortOrder(): Int
}
