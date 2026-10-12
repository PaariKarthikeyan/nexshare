package com.example.nexshare.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "peers")
data class PeerEntity(
    @PrimaryKey val peerId: String,
    val deviceName: String,
    val host: String,
    val port: Int,
    val method: String,
    val trusted: Boolean,
    val lastSeen: Long
)