package org.amisles.v4aw.ui.screen.siterules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.amisles.v4aw.i18n.LocalStrings
import org.amisles.v4aw.model.SiteRule
import org.amisles.v4aw.ui.theme.Slate50
import org.amisles.v4aw.ui.theme.Slate400
import org.amisles.v4aw.ui.theme.Slate500
import org.amisles.v4aw.ui.theme.Slate700
import org.amisles.v4aw.ui.theme.Slate800

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteRulesScreen(
    viewModel: SiteRulesViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAddRule: () -> Unit,
    onNavigateToEditRule: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val strings = LocalStrings.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, start = 4.dp, end = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = strings.backToOriginal,
                    tint = Slate800
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = strings.siteRulesManage,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Slate800,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onNavigateToAddRule) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = strings.siteRuleAdd,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.rules.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = strings.siteRuleNoRules,
                        style = MaterialTheme.typography.titleMedium,
                        color = Slate500
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = strings.siteRuleNoRulesHint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate400
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.rules, key = { it.id }) { rule ->
                    SiteRuleItem(
                        rule = rule,
                        onToggleEnabled = { viewModel.toggleRuleEnabled(rule) },
                        onEdit = { onNavigateToEditRule(rule.id) },
                        onDelete = { viewModel.showDeleteDialog(rule) }
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    uiState.showDeleteDialog?.let { rule ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteDialog() },
            title = { Text(strings.siteRuleDelete, fontWeight = FontWeight.Bold) },
            text = { Text(strings.siteRuleDeleteConfirm) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteRule(rule) }) {
                    Text(strings.delete, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteDialog() }) {
                    Text(strings.cancel, color = Slate500)
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun SiteRuleItem(
    rule: SiteRule,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val strings = LocalStrings.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (rule.enabled) androidx.compose.ui.graphics.Color.White else Slate50
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (rule.enabled) Slate800 else Slate400,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = rule.urlPattern,
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate500,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = { onToggleEnabled() },
                    modifier = Modifier.height(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Rule badges
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (rule.videoSourceRule != null) {
                    BadgeText(text = strings.siteRuleVideoSource)
                }
                if (rule.videoEntryRule != null) {
                    BadgeText(text = strings.siteRuleVideoEntry)
                }
                if (rule.searchEndpointRule != null) {
                    BadgeText(text = strings.siteRuleSearchEndpoint)
                }
                if (rule.webViewConfig != null) {
                    BadgeText(text = strings.siteRuleWebviewConfig)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = strings.siteRuleDelete,
                        tint = Slate400,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BadgeText(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
