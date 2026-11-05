package com.example.musicplayer.presentation.folders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Screen component providing first-run folder selection and permission recovery
 * state UIs.
 */
@Composable
fun FolderSelectionContent(
    onSelectFolderClicked: () -> Unit,
    modifier: Modifier = Modifier,
    isAccessRevoked: Boolean = false,
    folderDisplayName: String? = null,
    onClearFolderClicked: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val accent = if (isAccessRevoked) colors.error else colors.primary

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = if (isAccessRevoked) {
                        Icons.Rounded.FolderOff
                    } else {
                        Icons.Rounded.CreateNewFolder
                    },
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(44.dp),
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isAccessRevoked) "Folder Access Missing" else "Select Music Folder",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isAccessRevoked) {
                        "Access to '${folderDisplayName ?: "Music"}' was revoked or is no " +
                            "longer available. Choose the folder again to restore your library."
                    } else {
                        "Pick the folder that holds your music. Everything stays on this device."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onSelectFolderClicked,
                    modifier = Modifier.fillMaxWidth(0.85f),
                ) {
                    Text(if (isAccessRevoked) "Choose Folder Again" else "Select Folder")
                }

                if (isAccessRevoked && onClearFolderClicked != null) {
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = onClearFolderClicked,
                        modifier = Modifier.fillMaxWidth(0.85f),
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
    com.example.musicplayer.ui.theme.DoReMeDoReDoTheme {
        FolderSelectionContent(onSelectFolderClicked = {})
    }
}