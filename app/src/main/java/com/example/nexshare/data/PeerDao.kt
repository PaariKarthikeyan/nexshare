package com.example.nexshare.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PeerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(peer: PeerEntity)

    @Query("SELECT * FROM peers ORDER BY lastSeen DESC")
    fun observeAll(): Flow<List<PeerEntity>>

    @Query("SELECT * FROM peers WHERE peerId = :peerId")
    suspend fun get(peerId: String): PeerEntity?

    @Query("UPDATE peers SET trusted = :trusted WHERE peerId = :peerId")
    suspend fun setTrusted(peerId: String, trusted: Boolean)

    @Query("DELETE FROM peers WHERE peerId = :peerId")
    suspend fun delete(peerId: String)

    @Query("DELETE FROM peers WHERE trusted = 0")
    suspend fun clearUntrusted()
}