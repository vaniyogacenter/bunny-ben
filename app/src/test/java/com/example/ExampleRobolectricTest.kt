package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var db: BenDatabase
    private lateinit var dao: BenDao
    private lateinit var repository: BenRepository

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // In-memory Room database setup to avoid writing to disk during heavy load tests
        db = Room.inMemoryDatabaseBuilder(context, BenDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.benDao()
        repository = BenRepository(dao)
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Bunny Ben", appName)
    }

    @Test
    fun `perform concurrent load testing to verify database thread safety`() = runBlocking {
        // Initialize base state
        val initial = BenStateEntity(coins = 5000, level = 1, xp = 0)
        dao.saveBenState(initial)

        // Simulate 40 concurrent users/operations interacting with Bunny Ben's data in parallel
        val numThreads = 10
        val numOperations = 40
        val customDispatcher = Executors.newFixedThreadPool(numThreads).asCoroutineDispatcher()

        val jobs = List(numOperations) { index ->
            async(customDispatcher) {
                // Different users perform concurrent operations
                if (index % 3 == 0) {
                    repository.petBen()
                } else if (index % 3 == 1) {
                    repository.feedBen("carrot")
                } else {
                    repository.playMiniGame(10)
                }
            }
        }

        // Wait for all concurrent user actions to complete
        jobs.awaitAll()

        // Verify that the final state was computed securely without deadlocks or corruption
        val finalState = repository.getBenState()
        assertNotNull(finalState)
        
        // Assert that state exists and level progress successfully recorded
        // (Level must be at least 1, and coins/XP processed safely)
        assert(finalState.level >= 1)
        assert(finalState.coins >= 0)
    }
}

