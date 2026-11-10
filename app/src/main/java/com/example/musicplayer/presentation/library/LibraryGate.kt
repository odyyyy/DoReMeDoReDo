package com.example.musicplayer.presentation.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.musicplayer.presentation.components.LoadingStatus
import com.example.musicplayer.presentation.components.LocalArtworkRevision
import com.example.musicplayer.presentation.folders.FolderSelectionContent

/**
 * Wraps the library tabs with the folder lifecycle: loading, first-run folder
 * selection, revoked access, and a failed scan.
 *
 * Every tab shares this gate so folder problems are handled identically no matter
 * which tab the user is on, and so a scan failure is never hidden behind tab
 * content.
 *
 * @param content Rendered only while a readable folder is selected and no scan
 *   has failed.
 */
@Composable
fun LibraryGate(
    state: LibraryUiState,
    onFolderSelected: (Uri) -> Unit,
    onClearFolder: () -> Unit,
    onRescan: () -> Unit,
    content: @Composable (LibraryUiState.Ready) -> Unit,
) {
    val selectFolder = rememberFolderPicker(onFolderSelected)

    when (state) {
        LibraryUiState.Loading -> LoadingStatus(
            message = "Preparing your library…",
            modifier = Modifier.fillMaxSize().padding(top = 48.dp),
        )

        LibraryUiState.NoFolderSelected -> FolderSelectionContent(
            onSelectFolderClicked = selectFolder,
        )

        is LibraryUiState.FolderAccessRevoked -> FolderSelectionContent(
            isAccessRevoked = true,
            folderDisplayName = state.displayName,
            onSelectFolderClicked = selectFolder,
            onClearFolderClicked = onClearFolder,
        )

        is LibraryUiState.Ready -> {
            val failure = state.failure
            if (failure != null) {
                ScanFailedContent(
                    folderName = state.folder.displayName,
                    errorMessage = failure.message,
                    onRetryClicked = onRescan,
                    onChangeFolderClicked = selectFolder,
                )
            } else {
                // Artwork references are content addressed, so a rescan can
                // restore a missing image under the same URI. Bumping the
                // revision when the scan lifecycle changes makes rows that
                // already showed a placeholder retry the load.
                val artworkRevision = remember(state.scanState) { Any() }
                CompositionLocalProvider(LocalArtworkRevision provides artworkRevision) {
                    content(state)
                }
            }
        }
    }
}

/** Launches the system folder picker and reports the selected tree URI. */
@Composable
fun rememberFolderPicker(onFolderSelected: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) onFolderSelected(uri)
    }
    return remember(launcher) { { launcher.launch(null) } }
}

@Composable
private fun ScanFailedContent(
    folderName: String,
    errorMessage: String,
    onRetryClicked: () -> Unit,
    onChangeFolderClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Scan Failed",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Could not scan '$folderName': $errorMessage",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onRetryClicked) {
                        Text("Retry")
                    }
                    OutlinedButton(onClick = onChangeFolderClicked) {
                        Text("Change Folder")
                    }
                }
            }
        }
    }
}