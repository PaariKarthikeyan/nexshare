package com.example.nexshare

import android.app.Application

class NexShareApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Remove .part files and checkpoints left by an app session that ended mid-transfer.
        container.tempFiles.cleanAbandoned()
        container.checkpointCleanup()
    }
}