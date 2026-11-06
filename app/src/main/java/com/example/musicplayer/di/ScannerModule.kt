package com.example.musicplayer.di

import com.example.musicplayer.data.artwork.ArtworkStore
import com.example.musicplayer.data.artwork.FileArtworkStore
import com.example.musicplayer.data.scanner.AudioFileDiscovery
import com.example.musicplayer.data.scanner.MetadataExtraction
import com.example.musicplayer.data.scanner.MetadataExtractor
import com.example.musicplayer.data.scanner.MusicScanner
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ScannerModule {

    @Binds
    @Singleton
    abstract fun bindAudioFileDiscovery(impl: MusicScanner): AudioFileDiscovery

    @Binds
    @Singleton
    abstract fun bindMetadataExtraction(impl: MetadataExtractor): MetadataExtraction

    @Binds
    @Singleton
    abstract fun bindArtworkStore(impl: FileArtworkStore): ArtworkStore

    companion object {

        @Provides
        @Singleton
        fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
    }
}