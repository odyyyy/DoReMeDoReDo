package com.example.musicplayer.data.artwork

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Verifies the on-disk contract of [FileArtworkStore]: content addressing,
 * atomic writes, bounded cleanup and the guarantee that a foreign URI can never
 * keep (or delete) a local file.
 */
@RunWith(AndroidJUnit4::class)
class FileArtworkStoreTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = FileArtworkStore(context, Dispatchers.IO)
    private val directory = File(context.noBackupFilesDir, FileArtworkStore.DIRECTORY_NAME)

    @Before
    fun setUp() {
        directory.deleteRecursively()
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun store_writesOneContentAddressedFilePerImage() = runBlocking {
        val bytes = byteArrayOf(1, 2, 3, 4)

        val first = store.store(bytes)
        val second = store.store(bytes.copyOf())

        assertTrue(first.toString().startsWith("file://"))
        assertEquals("identical artwork must resolve to the same file", first, second)
        assertArrayEquals(bytes, fileOf(first).readBytes())
        assertEquals(1, directory.listFiles()?.size)
        // No temporary file survives a completed write.
        assertTrue(directory.listFiles().orEmpty().none { it.name.startsWith("tmp-") })
    }

    @Test
    fun store_writesDistinctFilesForDistinctImages() = runBlocking {
        val first = store.store(byteArrayOf(1))
        val second = store.store(byteArrayOf(2))

        assertNotEquals(first, second)
        assertEquals(2, directory.listFiles()?.size)
    }

    @Test
    fun store_returnsNullForEmptyImage() = runBlocking {
        assertNull(store.store(ByteArray(0)))
        assertFalse("nothing may be written for an empty image", directory.exists())
    }

    @Test
    fun isStored_reportsStoredMissingAndForeignUris() = runBlocking {
        val uri = requireNotNull(store.store(byteArrayOf(1, 2, 3)))
        assertTrue(store.isStored(uri))

        assertTrue(fileOf(uri).delete())
        assertFalse("a deleted image must not count as stored", store.isStored(uri))

        assertFalse(store.isStored("content://media/external/audio/albumart/1"))
        assertFalse(store.isStored("file:///artwork/missing.jpg"))
    }

    @Test
    fun deleteUnreferenced_removesOnlyUnreferencedFiles() = runBlocking {
        val keep = store.store(byteArrayOf(1))
        val drop = store.store(byteArrayOf(2))

        store.deleteUnreferenced(listOf(keep.toString()))

        assertTrue(fileOf(keep).isFile)
        assertFalse(fileOf(drop).exists())
    }

    @Test
    fun deleteUnreferenced_ignoresForeignUris() = runBlocking {
        val keep = store.store(byteArrayOf(1))

        store.deleteUnreferenced(
            listOf("content://media/external/audio/albumart/1", keep.toString()),
        )

        assertTrue(fileOf(keep).isFile)
    }

    @Test
    fun deleteUnreferenced_removesLeftoverTemporaryFiles() = runBlocking {
        val keep = store.store(byteArrayOf(1))
        directory.mkdirs()
        val leftover = File(directory, "tmp-interrupted.tmp").apply { writeBytes(byteArrayOf(9)) }
        assertTrue(leftover.isFile)

        store.deleteUnreferenced(listOf(keep.toString()))

        assertFalse("an interrupted write must be cleaned up", leftover.exists())
        assertTrue(fileOf(keep).isFile)
    }

    @Test
    fun deleteUnreferenced_withoutDirectoryOrReferences_isSafe() = runBlocking {
        // No store() call, so the directory does not exist yet.
        store.deleteUnreferenced(emptyList())

        assertFalse(directory.exists())
    }

    private fun fileOf(artworkUri: String?): File {
        val path = requireNotNull(Uri.parse(artworkUri).path) { "expected a file URI: $artworkUri" }
        return File(path)
    }
}
