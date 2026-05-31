package org.amisles.v4aw.ui.screen.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.amisles.v4aw.R
import org.amisles.v4aw.i18n.Language
import org.amisles.v4aw.i18n.LanguageViewModel
import org.amisles.v4aw.i18n.LocalLanguage
import org.amisles.v4aw.i18n.LocalStrings
import org.amisles.v4aw.ui.theme.Slate100
import org.amisles.v4aw.ui.theme.Slate400
import org.amisles.v4aw.ui.theme.Slate50
import org.amisles.v4aw.ui.theme.Slate500
import org.amisles.v4aw.ui.theme.Slate700
import org.amisles.v4aw.ui.theme.Slate800

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    languageViewModel: LanguageViewModel,
    onNavigateToLlmConfig: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onClearCache: () -> Unit,
    cacheSize: String = "0 MB",
    downloadPath: String = "",
    speedLimitKbps: Long = 0,
    onDownloadPathChange: (String) -> Unit = {},
    onResetDownloadPath: () -> Unit = {},
    onSpeedLimitChange: (Long) -> Unit = {}
) {
    val strings = LocalStrings.current
    val currentLanguage = LocalLanguage.current
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showDownloadPathDialog by remember { mutableStateOf(false) }
    var showSpeedLimitDialog by remember { mutableStateOf(false) }
    var downloadPathInput by remember(downloadPath) { mutableStateOf(downloadPath) }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = {
                Text(
                    text = strings.clearCacheTitle,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(strings.clearCacheMessage)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearCache()
                        showClearCacheDialog = false
                    }
                ) {
                    Text(strings.confirm, color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text(strings.cancel, color = Slate500)
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = {
                Text(
                    text = strings.languageSwitch,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Language.entries.forEach { language ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    languageViewModel.setLanguage(language)
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentLanguage == language,
                                onClick = {
                                    languageViewModel.setLanguage(language)
                                    showLanguageDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = language.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (currentLanguage == language) FontWeight.Bold else FontWeight.Normal,
                                color = if (currentLanguage == language) MaterialTheme.colorScheme.primary else Slate700
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(strings.cancel)
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showDownloadPathDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadPathDialog = false },
            title = {
                Text(
                    text = strings.downloadPathSetting,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = downloadPathInput,
                        onValueChange = { downloadPathInput = it },
                        label = { Text(strings.downloadPathHint) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = strings.downloadPathNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate500
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDownloadPathChange(downloadPathInput)
                        showDownloadPathDialog = false
                    }
                ) {
                    Text(strings.confirm, color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        onResetDownloadPath()
                        downloadPathInput = ""
                        showDownloadPathDialog = false
                    }) {
                        Text(strings.resetToDefault, color = Slate500)
                    }
                    TextButton(onClick = { showDownloadPathDialog = false }) {
                        Text(strings.cancel)
                    }
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showSpeedLimitDialog) {
        val speedOptions = listOf(0L, 256L, 512L, 1024L, 2048L, 4096L, 8192L, 16384L)
        AlertDialog(
            onDismissRequest = { showSpeedLimitDialog = false },
            title = {
                Text(
                    text = strings.speedLimitSetting,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    speedOptions.forEach { kbps ->
                        val label = if (kbps == 0L) strings.noLimit
                        else if (kbps >= 1024L) "${kbps / 1024} MB/s"
                        else "$kbps KB/s"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSpeedLimitChange(kbps)
                                    showSpeedLimitDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = speedLimitKbps == kbps,
                                onClick = {
                                    onSpeedLimitChange(kbps)
                                    showSpeedLimitDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (speedLimitKbps == kbps) FontWeight.Bold else FontWeight.Normal,
                                color = if (speedLimitKbps == kbps) MaterialTheme.colorScheme.primary else Slate700
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedLimitDialog = false }) {
                    Text(strings.cancel)
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        LogoHeader()

        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionLabel(text = strings.featureServices)

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate100)
            ) {
                Column {
                    MenuItemRow(
                        icon = Icons.Filled.Download,
                        title = strings.downloadManagement,
                        subtitle = strings.notAvailable,
                        enabled = false,
                        onClick = {}
                    )
                    MenuDivider()
                    MenuItemRow(
                        icon = Icons.Filled.FavoriteBorder,
                        title = strings.myFavorites,
                        subtitle = strings.notAvailable,
                        enabled = false,
                        onClick = {}
                    )
                    MenuDivider()
                    MenuItemRow(
                        icon = Icons.Filled.History,
                        title = strings.playHistory,
                        onClick = onNavigateToHistory
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            SectionLabel(text = strings.systemSettings)

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate100)
            ) {
                Column {
                    MenuItemRow(
                        icon = Icons.Filled.Language,
                        title = strings.languageSetting,
                        subtitle = currentLanguage.displayName,
                        onClick = { showLanguageDialog = true }
                    )
                    MenuDivider()
                    MenuItemRow(
                        icon = Icons.Filled.Settings,
                        title = strings.llmApiConfig,
                        onClick = onNavigateToLlmConfig
                    )
                    MenuDivider()
                    MenuItemRow(
                        icon = Icons.Filled.Folder,
                        title = strings.downloadPathSetting,
                        subtitle = downloadPath,
                        onClick = { showDownloadPathDialog = true }
                    )
                    MenuDivider()
                    MenuItemRow(
                        icon = Icons.Filled.Speed,
                        title = strings.speedLimitSetting,
                        subtitle = if (speedLimitKbps == 0L) strings.noLimit
                        else if (speedLimitKbps >= 1024L) "${speedLimitKbps / 1024} MB/s"
                        else "$speedLimitKbps KB/s",
                        onClick = { showSpeedLimitDialog = true }
                    )
                    MenuDivider()
                    MenuItemRow(
                        icon = Icons.Filled.Palette,
                        title = strings.appearanceSettings,
                        subtitle = strings.notAvailable,
                        enabled = false,
                        onClick = {}
                    )
                    MenuDivider()
                    MenuItemRow(
                        icon = Icons.Filled.DeleteSweep,
                        title = strings.clearCache,
                        subtitle = cacheSize,
                        onClick = { showClearCacheDialog = true }
                    )
                    MenuDivider()
                    MenuItemRow(
                        icon = Icons.Filled.Info,
                        title = strings.aboutApp,
                        onClick = onNavigateToAbout
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            DisclaimerCard()

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LogoHeader() {
    val strings = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_logo),
            contentDescription = "App Logo",
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = strings.appFullName,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            ),
            color = Slate800
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = strings.appSubtitle,
            style = MaterialTheme.typography.bodySmall,
            color = Slate500
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = Slate500,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun MenuItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val titleColor = if (enabled) Slate700 else Slate400
    val subtitleColor = if (enabled) Slate500 else Slate400

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.primary else Slate400,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = titleColor
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor,
                    maxLines = 1
                )
            }
        }
        if (enabled) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Slate400,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun MenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 52.dp),
        thickness = 0.5.dp,
        color = Slate100
    )
}

@Composable
private fun DisclaimerCard() {
    val strings = LocalStrings.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Slate50),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Slate100)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = null,
                    tint = Slate500,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = strings.securityDisclaimerTitle,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Slate700
                )
            }
            Text(
                text = strings.securityDisclaimerContent,
                style = MaterialTheme.typography.bodySmall,
                color = Slate500,
                lineHeight = 18.sp
            )
        }
    }
}
