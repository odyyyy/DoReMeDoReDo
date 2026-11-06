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
 */
@Database(
    entities = [TrackEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao

    companion object {
        const val NAME = "music_player.db"
    }
}
