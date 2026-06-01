package org.amisles.v4aw.ui.screen.videoplayer.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.amisles.v4aw.i18n.LocalStrings
import org.amisles.v4aw.ui.screen.videoplayer.VideoPlayerUiState
import org.amisles.v4aw.ui.screen.videoplayer.utils.formatTimeMs
import org.amisles.v4aw.ui.theme.Slate800

@Composable
fun AbLoopDialog(
    uiState: VideoPlayerUiState,
    onSetA: () -> Unit,
    onSetB: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    val aText = uiState.abLoopA?.let { strings.abLoopASet.format(formatTimeMs(it)) } ?: strings.abLoopANotSet
    val bText = uiState.abLoopB?.let { strings.abLoopBSet.format(formatTimeMs(it)) } ?: strings.abLoopBNotSet

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.abLoop) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (uiState.isAbLoopActive) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = androidx.compose.ui.graphics.Color(0xFFE8F5E9),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = strings.abLoopActive,
                            style = MaterialTheme.typography.bodyMedium,
                            color = androidx.compose.ui.graphics.Color(0xFF2E7D32),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = aText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = Slate800
                    )
                    OutlinedButton(onClick = onSetA) {
                        Text(strings.abLoopSetA)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = bText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = Slate800
                    )
                    OutlinedButton(
                        onClick = onSetB,
                        enabled = uiState.abLoopA != null
                    ) {
                        Text(strings.abLoopSetB)
                    }
                }
            }
        },
        confirmButton = {
            if (uiState.isAbLoopActive) {
                TextButton(onClick = onClear) {
                    Text(strings.abLoopCleared)
                }
            }
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}
