package com.example.musicplayer.data.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Local library database.
 *
 * Albums are intentionally not persisted as a separate table: they are derived
 * from track metadata. Schema exports are written to `app/schemas` so future
 * schema changes can be expressed as migrations.
 *
 * Version 2 adds `TrackEntity.artworkScanned`. Existing rows default to `0`, so
 * the next scan re-reads their artwork and backfills covers that were indexed
 * before the artwork pipeline existed. The added column only carries a boolean,
 * which is exactly the case Room auto-migrations handle safely.
 *
 * Version 3 adds `TrackEntity.trackNumber` and `TrackEntity.discNumber` (both
 * nullable) plus `orderScanned`, which defaults to `0`. Existing rows therefore
 * stay readable and are re-read once by the next scan to backfill their album
 * position; nothing else about the stored data changes.
 *
 * Version 4 adds `TrackEntity.fullArtworkUri` (nullable) and
 * `TrackEntity.fullArtworkScanned` (defaults to `0`). The next scan re-reads
 * rows whose full-resolution cover was never extracted, so Now Playing can show
 * a sharp cover while lists keep using the small thumbnail. Existing rows stay
 * readable and keep their thumbnail until that backfill runs.
 */
@Database(
    entities = [TrackEntity::class],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
    ],
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao

    companion object {
        const val NAME = "music_player.db"
    }
}
