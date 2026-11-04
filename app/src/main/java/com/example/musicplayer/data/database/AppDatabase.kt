package com.example.musicplayer.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Local library database.
 *
 * Albums are intentionally not persisted as a separate table: they are derived
 * from track metadata. Schema exports are written to `app/schemas` so future
 * schema changes can be expressed as migrations.
 */
@Database(
    entities = [TrackEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao

    companion object {
        const val NAME = "music_player.db"
    }
}
