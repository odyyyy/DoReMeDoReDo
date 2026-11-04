package com.example.musicplayer.data.scanner

import com.example.musicplayer.domain.model.DiscoveredAudioFile

/**
 * Reads metadata for discovered audio files.
 *
 * Implemented by [MetadataExtractor]. The narrow type keeps metadata reads
 * swappable so scan orchestration can be verified with fakes.
 */
interface MetadataExtraction {

    /**
     * Reads metadata for every file off the main thread, returning one result
     * per input file, in input order. A failure for one file never stops
     * extraction for the others.
     */
    suspend fun extractAll(files: List<DiscoveredAudioFile>): List<MetadataResult>
}