package com.example.musicplayer.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the automatic schema migrations shipped with the library database.
 *
 * The app has migrated 1 -> 2 -> 3 -> 4 in place, so an existing installation
 * must keep its indexed rows when it upgrades. The helper opens the exported
 * schema JSON files from `app/schemas`, applies the production auto-migrations,
 * and validates the resulting schema against version 4.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate1To4_keepsExistingTrackRowAndAddsNewColumns() {
        helper.createDatabase(TEST_DB, 1).use { database ->
            database.execSQL(
                """
                INSERT INTO tracks (
                    uri, title, artist, album, durationMs, albumId, artworkUri,
                    folderUri, fileSize, lastModified, dateAdded
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "content://music/legacy.mp3",
                    "Legacy Title",
                    "Legacy Artist",
                    "Legacy Album",
                    180_000L,
                    "legacy-album",
                    null,
                    "content://music/tree/legacy",
                    4_096L,
                    1_700_000_000_000L,
                    1_700_000_000_000L,
                ),
            )
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 4, true)

        migrated.query(
            """
            SELECT title, artist, album, durationMs, trackNumber, discNumber,
                   artworkScanned, fullArtworkUri, fullArtworkScanned, orderScanned
            FROM tracks WHERE uri = ?
            """.trimIndent(),
            arrayOf("content://music/legacy.mp3"),
        ).use { cursor ->
            assertTrue("legacy row must survive the migration", cursor.moveToFirst())
            assertEquals("Legacy Title", cursor.getString(0))
            assertEquals("Legacy Artist", cursor.getString(1))
            assertEquals("Legacy Album", cursor.getString(2))
            assertEquals(180_000L, cursor.getLong(3))
            assertTrue("trackNumber defaults to NULL", cursor.isNull(4))
            assertTrue("discNumber defaults to NULL", cursor.isNull(5))
            assertEquals("artwork must be re-read after upgrade", 0, cursor.getInt(6))
            assertTrue("full artwork starts absent", cursor.isNull(7))
            assertEquals("full artwork must be re-read after upgrade", 0, cursor.getInt(8))
            assertEquals("album order must be re-read after upgrade", 0, cursor.getInt(9))
        }
    }

    @Test
    fun migrate2To4_keepsArtworkUriAndDefaultsNewColumns() {
        helper.createDatabase(TEST_DB, 2).use { database ->
            database.execSQL(
                """
                INSERT INTO tracks (
                    uri, title, artist, album, durationMs, albumId, artworkUri,
                    artworkScanned, folderUri, fileSize, lastModified, dateAdded
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "content://music/v2.mp3",
                    "Version Two",
                    "Artist",
                    "Album",
                    90_000L,
                    null,
                    "file:///artwork/v2.jpg",
                    1,
                    "content://music/tree/v2",
                    1_024L,
                    1_700_000_000_000L,
                    1_700_000_000_000L,
                ),
            )
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 4, true)

        migrated.query(
            "SELECT artworkUri, artworkScanned, trackNumber, fullArtworkUri FROM tracks",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("file:///artwork/v2.jpg", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
            assertTrue(cursor.isNull(2))
            assertTrue(cursor.isNull(3))
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
