package org.amisles.v4aw.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.amisles.v4aw.model.VideoEntry
import org.amisles.v4aw.model.SearchEndpoint
import org.amisles.v4aw.i18n.LocalStrings
import org.amisles.v4aw.ui.theme.Slate50
import org.amisles.v4aw.ui.theme.Slate500
import org.amisles.v4aw.ui.theme.Slate800

@Composable
fun VideoEntryItem(
    entry: VideoEntry,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Slate50),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (entry.thumbnailUrl != null) {
                AsyncImage(
                    model = entry.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(100.dp, 60.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Slate800,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteSearchCard(
    searchEndpoints: List<SearchEndpoint>,
    isSearching: Boolean,
    errorMessage: String?,
    onSearch: (SearchEndpoint, String) -> Unit
) {
    val strings = LocalStrings.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedEndpointIndex by remember { mutableIntStateOf(0) }
    val selectedEndpoint = searchEndpoints.getOrElse(selectedEndpointIndex) { searchEndpoints.firstOrNull() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Slate50),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = strings.siteSearch,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Slate800
            )

            if (searchEndpoints.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    searchEndpoints.forEachIndexed { index, endpoint ->
                        val isSelected = index == selectedEndpointIndex
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedEndpointIndex = index },
                            label = {
                                Text(
                                    text = try {
                                        java.net.URL(endpoint.actionUrl).host
                                    } catch (_: Exception) {
                                        endpoint.actionUrl.take(30)
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = selectedEndpoint?.placeholder ?: strings.searchHint,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    enabled = !isSearching,
                    shape = RoundedCornerShape(16.dp)
                )

                Button(
                    onClick = {
                        if (searchQuery.isNotBlank() && selectedEndpoint != null) {
                            onSearch(selectedEndpoint, searchQuery.trim())
                        }
                    },
                    enabled = !isSearching && searchQuery.isNotBlank() && selectedEndpoint != null,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(strings.searchButton, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
