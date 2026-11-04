package com.example.musicplayer.presentation.folders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Screen component providing first-run folder selection and permission recovery state UIs.
 */
@Composable
fun FolderSelectionContent(
    onSelectFolderClicked: () -> Unit,
    modifier: Modifier = Modifier,
    isAccessRevoked: Boolean = false,
    folderDisplayName: String? = null,
    onClearFolderClicked: (() -> Unit)? = null,
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
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (isAccessRevoked) "⚠️" else "📁",
                    style = MaterialTheme.typography.displayMedium,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isAccessRevoked) {
                        "Folder Access Missing"
                    } else {
                        "Select Music Folder"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isAccessRevoked) {
                        "Access to folder '${folderDisplayName ?: "Music"}' has been revoked or is no longer accessible. Please reselect your music folder to restore access."
                    } else {
                        "Choose a folder containing your music files to start building your local offline music library."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onSelectFolderClicked,
                    modifier = Modifier.fillMaxWidth(0.8f),
                ) {
                    Text(
                        text = if (isAccessRevoked) "Reselect Folder" else "Select Folder"
                    )
                }

                if (isAccessRevoked && onClearFolderClicked != null) {
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = onClearFolderClicked,
                        modifier = Modifier.fillMaxWidth(0.8f),
                    ) {
                        Text("Clear Selection")
                    }
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun FolderSelectionContentPreview() {
    com.example.musicplayer.ui.theme.MusicPlayerTheme {
        FolderSelectionContent(onSelectFolderClicked = {})
    }
}
