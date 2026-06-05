package org.amisles.v4aw.ui.screen.siterules

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.amisles.v4aw.i18n.LocalStrings
import org.amisles.v4aw.model.VideoSourceRule
import org.amisles.v4aw.model.VideoEntryRule
import org.amisles.v4aw.model.SearchEndpointRule
import org.amisles.v4aw.model.WebViewConfigRule
import org.amisles.v4aw.ui.theme.Slate500
import org.amisles.v4aw.ui.theme.Slate700
import org.amisles.v4aw.ui.theme.Slate800

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteRuleEditScreen(
    viewModel: SiteRuleEditViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val strings = LocalStrings.current
    val rule = uiState.rule

    // Navigate back after save
    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onNavigateBack()
        }
    }

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
                text = if (uiState.isNewRule) strings.siteRuleAdd else strings.siteRuleEdit,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Slate800,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = { viewModel.saveRule() },
                enabled = !uiState.isSaving
            ) {
                Text(strings.siteRuleSave, fontWeight = FontWeight.Bold)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Basic Info Section
            SectionHeader(title = strings.siteRuleEdit)

            OutlinedTextField(
                value = rule.name,
                onValueChange = { viewModel.updateName(it) },
                label = { Text(strings.siteRuleName) },
                isError = uiState.nameError != null,
                supportingText = if (uiState.nameError != null) {{ Text(strings.siteRuleNameRequired) }} else null,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = rule.urlPattern,
                onValueChange = { viewModel.updateUrlPattern(it) },
                label = { Text(strings.siteRuleUrlPattern) },
                isError = uiState.patternError != null,
                supportingText = if (uiState.patternError != null) {{ Text(strings.siteRulePatternRequired) }} else null,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.siteRulePriority,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Slate700,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = { viewModel.updateEnabled(it) }
                )
            }

            OutlinedTextField(
                value = rule.priority.toString(),
                onValueChange = { viewModel.updatePriority(it.toIntOrNull() ?: 0) },
                label = { Text(strings.siteRulePriority) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            HorizontalDivider()

            // Video Source Rule Section
            var showVideoSourceRule by remember { mutableStateOf(rule.videoSourceRule != null) }
            var videoSourceSelectors by remember(rule.videoSourceRule) {
                mutableStateOf(rule.videoSourceRule?.selectors?.joinToString("\n") ?: "")
            }
            var videoSourceAttrs by remember(rule.videoSourceRule) {
                mutableStateOf(rule.videoSourceRule?.customAttributes?.joinToString("\n") ?: "")
            }
            var videoSourcePatterns by remember(rule.videoSourceRule) {
                mutableStateOf(rule.videoSourceRule?.scriptUrlPatterns?.joinToString("\n") ?: "")
            }
            var videoSourceIframeParams by remember(rule.videoSourceRule) {
                mutableStateOf(rule.videoSourceRule?.iframeUrlParams?.joinToString("\n") ?: "")
            }

            SectionHeader(title = strings.siteRuleVideoSource)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.siteRuleEnabled,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Slate700,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = showVideoSourceRule,
                    onCheckedChange = { enabled ->
                        showVideoSourceRule = enabled
                        if (!enabled) viewModel.updateVideoSourceRule(null)
                        else viewModel.updateVideoSourceRule(VideoSourceRule())
                    }
                )
            }

            if (showVideoSourceRule) {
                OutlinedTextField(
                    value = videoSourceSelectors,
                    onValueChange = {
                        videoSourceSelectors = it
                        val selectors = it.lines().filter { l -> l.isNotBlank() }
                        viewModel.updateVideoSourceRule(
                            (rule.videoSourceRule ?: VideoSourceRule()).copy(selectors = selectors)
                        )
                    },
                    label = { Text(strings.siteRuleCssSelectors) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp),
                    supportingText = { Text("One selector per line") }
                )

                OutlinedTextField(
                    value = videoSourceAttrs,
                    onValueChange = {
                        videoSourceAttrs = it
                        val attrs = it.lines().filter { l -> l.isNotBlank() }
                        viewModel.updateVideoSourceRule(
                            (rule.videoSourceRule ?: VideoSourceRule()).copy(customAttributes = attrs)
                        )
                    },
                    label = { Text(strings.siteRuleCustomAttributes) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp),
                    supportingText = { Text("One attribute per line") }
                )

                OutlinedTextField(
                    value = videoSourcePatterns,
                    onValueChange = {
                        videoSourcePatterns = it
                        val patterns = it.lines().filter { l -> l.isNotBlank() }
                        viewModel.updateVideoSourceRule(
                            (rule.videoSourceRule ?: VideoSourceRule()).copy(scriptUrlPatterns = patterns)
                        )
                    },
                    label = { Text(strings.siteRuleScriptUrlPatterns) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp),
                    supportingText = { Text("One regex per line") }
                )

                OutlinedTextField(
                    value = videoSourceIframeParams,
                    onValueChange = {
                        videoSourceIframeParams = it
                        val params = it.lines().filter { l -> l.isNotBlank() }
                        viewModel.updateVideoSourceRule(
                            (rule.videoSourceRule ?: VideoSourceRule()).copy(iframeUrlParams = params)
                        )
                    },
                    label = { Text(strings.siteRuleIframeUrlParams) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp),
                    supportingText = { Text("One param name per line") }
                )
            }

            HorizontalDivider()

            // Video Entry Rule Section
            var showVideoEntryRule by remember { mutableStateOf(rule.videoEntryRule != null) }
            var entryContainer by remember(rule.videoEntryRule) {
                mutableStateOf(rule.videoEntryRule?.containerSelector ?: "")
            }
            var entryLink by remember(rule.videoEntryRule) {
                mutableStateOf(rule.videoEntryRule?.linkSelector ?: "a")
            }
            var entryTitle by remember(rule.videoEntryRule) {
                mutableStateOf(rule.videoEntryRule?.titleSelector ?: "")
            }
            var entryThumbnail by remember(rule.videoEntryRule) {
                mutableStateOf(rule.videoEntryRule?.thumbnailSelector ?: "img")
            }

            SectionHeader(title = strings.siteRuleVideoEntry)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.siteRuleEnabled,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Slate700,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = showVideoEntryRule,
                    onCheckedChange = { enabled ->
                        showVideoEntryRule = enabled
                        if (!enabled) viewModel.updateVideoEntryRule(null)
                        else viewModel.updateVideoEntryRule(VideoEntryRule())
                    }
                )
            }

            if (showVideoEntryRule) {
                OutlinedTextField(
                    value = entryContainer,
                    onValueChange = {
                        entryContainer = it
                        viewModel.updateVideoEntryRule(
                            (rule.videoEntryRule ?: VideoEntryRule()).copy(containerSelector = it)
                        )
                    },
                    label = { Text(strings.siteRuleContainerSelector) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = entryLink,
                    onValueChange = {
                        entryLink = it
                        viewModel.updateVideoEntryRule(
                            (rule.videoEntryRule ?: VideoEntryRule()).copy(linkSelector = it)
                        )
                    },
                    label = { Text(strings.siteRuleLinkSelector) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = entryTitle,
                    onValueChange = {
                        entryTitle = it
                        viewModel.updateVideoEntryRule(
                            (rule.videoEntryRule ?: VideoEntryRule()).copy(titleSelector = it)
                        )
                    },
                    label = { Text(strings.siteRuleTitleSelector) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = entryThumbnail,
                    onValueChange = {
                        entryThumbnail = it
                        viewModel.updateVideoEntryRule(
                            (rule.videoEntryRule ?: VideoEntryRule()).copy(thumbnailSelector = it)
                        )
                    },
                    label = { Text(strings.siteRuleThumbnailSelector) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            HorizontalDivider()

            // Search Endpoint Rule Section
            var showSearchRule by remember { mutableStateOf(rule.searchEndpointRule != null) }
            var searchTemplate by remember(rule.searchEndpointRule) {
                mutableStateOf(rule.searchEndpointRule?.searchUrlTemplate ?: "")
            }
            var searchMethod by remember(rule.searchEndpointRule) {
                mutableStateOf(rule.searchEndpointRule?.method ?: "GET")
            }

            SectionHeader(title = strings.siteRuleSearchEndpoint)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.siteRuleEnabled,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Slate700,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = showSearchRule,
                    onCheckedChange = { enabled ->
                        showSearchRule = enabled
                        if (!enabled) viewModel.updateSearchEndpointRule(null)
                        else viewModel.updateSearchEndpointRule(SearchEndpointRule())
                    }
                )
            }

            if (showSearchRule) {
                OutlinedTextField(
                    value = searchTemplate,
                    onValueChange = {
                        searchTemplate = it
                        viewModel.updateSearchEndpointRule(
                            (rule.searchEndpointRule ?: SearchEndpointRule()).copy(searchUrlTemplate = it)
                        )
                    },
                    label = { Text(strings.siteRuleSearchUrlTemplate) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    supportingText = { Text("Use {query} as placeholder") }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.siteRuleSearchMethod,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Slate700,
                        modifier = Modifier.weight(1f)
                    )
                    Row {
                        FilterChip(
                            selected = searchMethod == "GET",
                            onClick = {
                                searchMethod = "GET"
                                viewModel.updateSearchEndpointRule(
                                    (rule.searchEndpointRule ?: SearchEndpointRule()).copy(method = "GET")
                                )
                            },
                            label = { Text("GET") }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(
                            selected = searchMethod == "POST",
                            onClick = {
                                searchMethod = "POST"
                                viewModel.updateSearchEndpointRule(
                                    (rule.searchEndpointRule ?: SearchEndpointRule()).copy(method = "POST")
                                )
                            },
                            label = { Text("POST") }
                        )
                    }
                }
            }

            HorizontalDivider()

            // WebView Config Section
            var showWebViewConfig by remember { mutableStateOf(rule.webViewConfig != null) }
            var pageLoadDelay by remember(rule.webViewConfig) {
                mutableStateOf(rule.webViewConfig?.pageLoadDelay?.toString() ?: "")
            }
            var scrollBeforeExtract by remember(rule.webViewConfig) {
                mutableStateOf(rule.webViewConfig?.scrollBeforeExtract ?: false)
            }
            var customUserAgent by remember(rule.webViewConfig) {
                mutableStateOf(rule.webViewConfig?.customUserAgent ?: "")
            }
            var disableAdBlock by remember(rule.webViewConfig) {
                mutableStateOf(rule.webViewConfig?.disableAdBlock ?: false)
            }

            SectionHeader(title = strings.siteRuleWebviewConfig)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.siteRuleEnabled,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Slate700,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = showWebViewConfig,
                    onCheckedChange = { enabled ->
                        showWebViewConfig = enabled
                        if (!enabled) viewModel.updateWebViewConfig(null)
                        else viewModel.updateWebViewConfig(WebViewConfigRule())
                    }
                )
            }

            if (showWebViewConfig) {
                OutlinedTextField(
                    value = pageLoadDelay,
                    onValueChange = {
                        pageLoadDelay = it
                        viewModel.updateWebViewConfig(
                            (rule.webViewConfig ?: WebViewConfigRule()).copy(
                                pageLoadDelay = it.toLongOrNull()
                            )
                        )
                    },
                    label = { Text(strings.siteRulePageLoadDelay) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.siteRuleScrollBeforeExtract,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Slate700,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = scrollBeforeExtract,
                        onCheckedChange = {
                            scrollBeforeExtract = it
                            viewModel.updateWebViewConfig(
                                (rule.webViewConfig ?: WebViewConfigRule()).copy(scrollBeforeExtract = it)
                            )
                        }
                    )
                }

                OutlinedTextField(
                    value = customUserAgent,
                    onValueChange = {
                        customUserAgent = it
                        viewModel.updateWebViewConfig(
                            (rule.webViewConfig ?: WebViewConfigRule()).copy(
                                customUserAgent = it.ifEmpty { null }
                            )
                        )
                    },
                    label = { Text(strings.siteRuleCustomUserAgent) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.siteRuleDisableAdBlock,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Slate700,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = disableAdBlock,
                        onCheckedChange = {
                            disableAdBlock = it
                            viewModel.updateWebViewConfig(
                                (rule.webViewConfig ?: WebViewConfigRule()).copy(disableAdBlock = it)
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = Slate800,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}
