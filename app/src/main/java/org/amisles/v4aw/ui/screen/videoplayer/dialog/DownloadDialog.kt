package org.amisles.v4aw.ui.screen.videoplayer.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.amisles.v4aw.model.VideoInfo
import org.amisles.v4aw.i18n.LocalStrings
import org.amisles.v4aw.ui.screen.videoplayer.utils.isDownloadableSource
import org.amisles.v4aw.ui.screen.videoplayer.utils.isHlsSource
import org.amisles.v4aw.ui.screen.videoplayer.utils.isDashSource
import org.amisles.v4aw.ui.screen.videoplayer.utils.isStreamingSource
import org.amisles.v4aw.ui.theme.Slate50
import org.amisles.v4aw.ui.theme.Slate500
import org.amisles.v4aw.ui.theme.Slate800

@Composable
fun DownloadDialog(
    videoInfo: VideoInfo,
    availableSources: List<String>,
    onDismiss: () -> Unit,
    onDownload: (String) -> Unit
) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.selectSourceToDownload) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = videoInfo.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Slate800,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Slate50,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = strings.downloadTip,
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate500,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (availableSources.isEmpty()) {
                    Text(strings.noAvailableSources)
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 300.dp)
                    ) {
                        items(availableSources) { source ->
                            val displayName = if (source.length > 50) {
                                source.take(50) + "..."
                            } else {
                                source
                            }
                            val isDownloadable = isDownloadableSource(source)
                            val isHls = isHlsSource(source)
                            val isDash = isDashSource(source)
                            val isStreaming = isStreamingSource(source)

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = isDownloadable) {
                                        if (isDownloadable) {
                                            onDownload(source)
                                        }
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDownloadable) {
                                        if (isHls) {
                                            androidx.compose.ui.graphics.Color(0xFFFFF8E1)
                                        } else if (isDash) {
                                            androidx.compose.ui.graphics.Color(0xFFE3F2FD)
                                        } else {
                                            androidx.compose.ui.graphics.Color(0xFFE8F5E9)
                                        }
                                    } else if (isStreaming) {
                                        androidx.compose.ui.graphics.Color(0xFFFFEBEE)
                                    } else {
                                        Slate50
                                    }
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text(
                                        text = displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isDownloadable) Slate800 else Slate500
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isDownloadable) {
                                            if (isHls) {
                                                Icon(
                                                    Icons.Default.Download,
                                                    contentDescription = strings.hlsDownloadable,
                                                    tint = androidx.compose.ui.graphics.Color(0xFFFF9800),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = strings.hlsDownloadable,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = androidx.compose.ui.graphics.Color(0xFFFF9800)
                                                )
                                            } else if (isDash) {
                                                Icon(
                                                    Icons.Default.Download,
                                                    contentDescription = strings.dashDownloadable,
                                                    tint = androidx.compose.ui.graphics.Color(0xFF1976D2),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = strings.dashDownloadable,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = androidx.compose.ui.graphics.Color(0xFF1976D2)
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Default.CheckCircle,
                                                    contentDescription = strings.downloadable,
                                                    tint = androidx.compose.ui.graphics.Color(0xFF4CAF50),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = strings.downloadable,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = androidx.compose.ui.graphics.Color(0xFF4CAF50)
                                                )
                                            }
                                        } else if (isStreaming) {
                                            Icon(
                                                Icons.Default.Warning,
                                                contentDescription = strings.streamingFormat,
                                                tint = androidx.compose.ui.graphics.Color(0xFFF44336),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = strings.streamingFormat,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = androidx.compose.ui.graphics.Color(0xFFF44336)
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.HelpOutline,
                                                contentDescription = strings.unknownFormat,
                                                tint = Slate500,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = strings.unknownFormat,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Slate500
                                            )
                                        }
                                    }
                                }
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
