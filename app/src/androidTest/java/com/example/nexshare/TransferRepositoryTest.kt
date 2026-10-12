package com.example.nexshare

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.nexshare.data.AppDatabase
import com.example.nexshare.transfer.TransferRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransferRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: TransferRepository

    @Before
    fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        repo = TransferRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun recordsStartAndFinish() = runBlocking {
        repo.recordStart("t1", "photo.jpg", 1234L, "a".repeat(64), "Me", "Them", "HOTSPOT", "SENT")
        repo.recordFinished("t1", TransferRepository.STATUS_VERIFIED)

        val list = repo.observeTransfers().first()
        assertEquals(1, list.size)
        assertEquals(TransferRepository.STATUS_VERIFIED, list[0].status)
        assertNotNull(list[0].completedAt)
    }

    @Test
    fun upsertPeerKeepsTrustFlag() = runBlocking {
        repo.upsertPeer("p1", "Phone", "10.0.0.2", 5000, "HOTSPOT")
        repo.setTrusted("p1", true)
        repo.upsertPeer("p1", "Phone", "10.0.0.3", 5001, "HOTSPOT")

        val peer = repo.observePeers().first().single()
        assertEquals(true, peer.trusted)
        assertEquals("10.0.0.3", peer.host)
    }
}
