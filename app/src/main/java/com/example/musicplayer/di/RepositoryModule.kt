package com.example.musicplayer.di

import com.example.musicplayer.data.repository.FolderRepositoryImpl
import com.example.musicplayer.data.repository.ScanStatusRepositoryImpl
import com.example.musicplayer.data.repository.TrackRepository
import com.example.musicplayer.data.repository.TrackRepositoryImpl
import com.example.musicplayer.domain.repository.FolderRepository
import com.example.musicplayer.domain.repository.ScanStatusRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindFolderRepository(
        impl: FolderRepositoryImpl,
    ): FolderRepository

    @Binds
    @Singleton
    abstract fun bindTrackRepository(
        impl: TrackRepositoryImpl,
    ): TrackRepository

    @Binds
    @Singleton
    abstract fun bindScanStatusRepository(
        impl: ScanStatusRepositoryImpl,
    ): ScanStatusRepository
}
