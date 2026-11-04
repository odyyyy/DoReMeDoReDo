package com.example.musicplayer.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.musicplayer.domain.model.CompletedScan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Verifies the database-first startup marker: a completed scan is persisted in
 * DataStore and read back by a *fresh* repository instance, which is what an
 * app restart looks like from the marker's point of view.
 */
@RunWith(AndroidJUnit4::class)
class ScanStatusRepositoryImplTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var dataStoreFile: File
    private var scope: CoroutineScope? = null

    @Before
    fun setUp() {
        val dir = File(context.cacheDir, "scan-status-test").apply { mkdirs() }
        dataStoreFile = File(dir, "scan_status.preferences_pb")
        closeStore()
        dataStoreFile.delete()
    }

    @After
    fun tearDown() {
        closeStore()
        dataStoreFile.delete()
    }

    @Test
    fun marker_survivesProcessDeath() = runBlocking {
        val repository = ScanStatusRepositoryImpl(openStore())
        assertNull(repository.lastCompletedScan.first())

        repository.markCompleted(folderUri = FOLDER, completedAt = COMPLETED_AT)

        // Simulate process death: drop the DataStore instance, then open the
        // same file again the way a restarted app would.
        closeStore()
        val restarted = ScanStatusRepositoryImpl(openStore())

        val marker = restarted.lastCompletedScan.first()
        assertEquals(CompletedScan(folderUri = FOLDER, completedAt = COMPLETED_AT), marker)
    }

    @Test
    fun missingOrUnreadableMarker_resolvesToNull() = runBlocking {
        val repository = ScanStatusRepositoryImpl(openStore())
        // No marker stored yet: the app must treat the folder as not indexed.
        assertNull(repository.lastCompletedScan.first())
    }

    // --- helpers -------------------------------------------------------------

    private fun openStore(): DataStore<Preferences> {
        val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = retryOnActiveFile {
            PreferenceDataStoreFactory.create(scope = coroutineScope) { dataStoreFile }
        }
        scope = coroutineScope
        return store
    }

    /** Cancels the current DataStore scope and waits for it to release the file. */
    private fun closeStore() {
        val coroutineScope = scope ?: return
        scope = null
        coroutineScope.cancel()
        val job: Job = coroutineScope.coroutineContext.job
        runBlocking { job.join() }
    }

    /**
     * DataStore refuses a second live instance for the same file until the
     * previous scope has fully completed, so briefly retry around creation.
     */
    private fun retryOnActiveFile(create: () -> DataStore<Preferences>): DataStore<Preferences> {
        var lastError: Throwable? = null
        repeat(20) {
            try {
                return create()
            } catch (e: IllegalStateException) {
                lastError = e
                Thread.sleep(50)
            }
        }
        throw lastError ?: IllegalStateException("could not open the DataStore")
    }

    private companion object {
        const val FOLDER =
            "content://com.example.musicplayer.documents/tree/music"
        const val COMPLETED_AT = 1_700_000_000_000L
    }
}