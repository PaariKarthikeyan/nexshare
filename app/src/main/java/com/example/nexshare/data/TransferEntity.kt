package com.example.nexshare.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Metadata only. The spec forbids storing file contents here.
 * Fields match the required transfer record: ID, name, size, hash,
 * peers, method, status, speed, and timestamps.
 */
@Entity(tableName = "transfers")
data class TransferEntity(
    @PrimaryKey val transferId: String,
    val fileName: String,
    val fileSize: Long,
    val sha256: String,
    val sender: String,
    val receiver: String,
    val method: String,
    val direction: String,
    val status: String,
    val speedBytesPerSecond: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?
)