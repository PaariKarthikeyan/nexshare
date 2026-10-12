package com.example.nexshare.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(transfer: TransferEntity)

    @Query("SELECT * FROM transfers ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers WHERE status IN (:statuses) ORDER BY createdAt DESC")
    fun observeByStatus(statuses: List<String>): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers WHERE transferId = :transferId")
    suspend fun get(transferId: String): TransferEntity?

    @Query("UPDATE transfers SET status = :status, speedBytesPerSecond = :speed, updatedAt = :now WHERE transferId = :transferId")
    suspend fun updateStatus(transferId: String, status: String, speed: Long, now: Long)

    @Query("UPDATE transfers SET status = :status, speedBytesPerSecond = 0, updatedAt = :now, completedAt = :now WHERE transferId = :transferId")
    suspend fun markFinished(transferId: String, status: String, now: Long)

    @Query("DELETE FROM transfers")
    suspend fun clearAll()
}