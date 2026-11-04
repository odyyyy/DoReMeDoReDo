package com.example.musicplayer.domain.usecase

import android.net.Uri
import com.example.musicplayer.data.scanner.MusicScanner
import com.example.musicplayer.data.scanner.ScanResult
import javax.inject.Inject

/**
 * Executes SAF tree traversal to discover audio candidates in the specified folder.
 */
class ScanMusicFolderUseCase @Inject constructor(
    private val musicScanner: MusicScanner,
) {
    suspend operator fun invoke(folderUri: Uri): ScanResult {
        return musicScanner.scanFolder(folderUri)
    }
}
