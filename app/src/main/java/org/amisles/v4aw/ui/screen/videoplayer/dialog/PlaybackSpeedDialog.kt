package org.amisles.v4aw.ui.screen.videoplayer.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.amisles.v4aw.i18n.LocalStrings
import org.amisles.v4aw.ui.screen.videoplayer.utils.PLAYBACK_SPEEDS
import org.amisles.v4aw.ui.screen.videoplayer.utils.formatSpeedText
import org.amisles.v4aw.ui.theme.Slate800

@Composable
fun PlaybackSpeedDialog(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.playbackSpeed) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PLAYBACK_SPEEDS.forEach { speed ->
                    val isSelected = speed == currentSpeed
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSpeedSelected(speed) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) {
                            androidx.compose.ui.graphics.Color(0xFFE8F5E9)
                        } else {
                            androidx.compose.ui.graphics.Color.Transparent
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatSpeedText(speed),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) {
                                    androidx.compose.ui.graphics.Color(0xFF2E7D32)
                                } else {
                                    Slate800
                                }
                            )
                            if (isSelected) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = androidx.compose.ui.graphics.Color(0xFF2E7D32),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}
