package com.example.musicplayer.di

import android.content.Context
import androidx.room.Room
import com.example.musicplayer.data.database.AppDatabase
import com.example.musicplayer.data.database.TrackDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.NAME,
        ).build()

    @Provides
    fun provideTrackDao(database: AppDatabase): TrackDao = database.trackDao()
}
