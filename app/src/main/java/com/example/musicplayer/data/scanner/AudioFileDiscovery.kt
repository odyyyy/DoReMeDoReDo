package com.example.musicplayer.data.scanner

import android.net.Uri

/**
 * Discovers candidate audio files inside a SAF document tree.
 *
 * Implemented by [MusicScanner]. The narrow type lets scan orchestration be
 * verified with fakes instead of a real document provider.
 */
interface AudioFileDiscovery {

    /**
     * Traverses the folder tree of [folderUri] for supported audio files.
     *
     * @return A [ScanResult.Success] containing discovered audio files, or
     *   [ScanResult.Failure] when the folder cannot be read at all.
     */
    suspend fun scanFolder(folderUri: Uri): ScanResult
}