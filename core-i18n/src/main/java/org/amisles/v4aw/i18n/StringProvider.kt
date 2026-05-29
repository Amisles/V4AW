package org.amisles.v4aw.i18n

interface StringProvider {
    fun get(key: String): String
    val currentLanguage: Language

    val appName get() = get(StringKey.APP_NAME)
    val appFullName get() = get(StringKey.APP_FULL_NAME)
    val appSubtitle get() = get(StringKey.APP_SUBTITLE)

    val home get() = get(StringKey.HOME)
    val history get() = get(StringKey.HISTORY)
    val downloads get() = get(StringKey.DOWNLOADS)
    val profile get() = get(StringKey.PROFILE)

    val getVideo get() = get(StringKey.GET_VIDEO)
    val pasteUrlHint get() = get(StringKey.PASTE_URL_HINT)
    val parse get() = get(StringKey.PARSE)
    val parsing get() = get(StringKey.PARSING)

    val usageGuide get() = get(StringKey.USAGE_GUIDE)
    val step1CopyLink get() = get(StringKey.STEP1_COPY_LINK)
    val step2PasteUrl get() = get(StringKey.STEP2_PASTE_URL)
    val step3ParseEnjoy get() = get(StringKey.STEP3_PARSE_ENJOY)

    val errorEmptyUrl get() = get(StringKey.ERROR_EMPTY_URL)
    val errorInvalidUrl get() = get(StringKey.ERROR_INVALID_URL)
    val errorParseFailed get() = get(StringKey.ERROR_PARSE_FAILED)

    val historyTitle get() = get(StringKey.HISTORY_TITLE)
    val noHistory get() = get(StringKey.NO_HISTORY)
    val noHistoryHint get() = get(StringKey.NO_HISTORY_HINT)
    val selectedCount get() = get(StringKey.SELECTED_COUNT)
    val selectAll get() = get(StringKey.SELECT_ALL)
    val cancelSelection get() = get(StringKey.CANCEL_SELECTION)
    val select get() = get(StringKey.SELECT)
    val clearAll get() = get(StringKey.CLEAR_ALL)
    val deleteSelected get() = get(StringKey.DELETE_SELECTED)
    val deleteConfirmSelected get() = get(StringKey.DELETE_CONFIRM_SELECTED)
    val deleteConfirmAll get() = get(StringKey.DELETE_CONFIRM_ALL)
    val delete get() = get(StringKey.DELETE)
    val cancel get() = get(StringKey.CANCEL)
    val confirm get() = get(StringKey.CONFIRM)

    val featureServices get() = get(StringKey.FEATURE_SERVICES)
    val downloadManagement get() = get(StringKey.DOWNLOAD_MANAGEMENT)
    val notAvailable get() = get(StringKey.NOT_AVAILABLE)
    val myFavorites get() = get(StringKey.MY_FAVORITES)
    val playHistory get() = get(StringKey.PLAY_HISTORY)

    val systemSettings get() = get(StringKey.SYSTEM_SETTINGS)
    val llmApiConfig get() = get(StringKey.LLM_API_CONFIG)
    val appearanceSettings get() = get(StringKey.APPEARANCE_SETTINGS)
    val clearCache get() = get(StringKey.CLEAR_CACHE)
    val aboutApp get() = get(StringKey.ABOUT_APP)

    val clearCacheTitle get() = get(StringKey.CLEAR_CACHE_TITLE)
    val clearCacheMessage get() = get(StringKey.CLEAR_CACHE_MESSAGE)
    val currentCacheSize get() = get(StringKey.CURRENT_CACHE_SIZE)

    val securityDisclaimerTitle get() = get(StringKey.SECURITY_DISCLAIMER_TITLE)
    val securityDisclaimerContent get() = get(StringKey.SECURITY_DISCLAIMER_CONTENT)

    val aboutAppTitle get() = get(StringKey.ABOUT_APP_TITLE)
    val version get() = get(StringKey.VERSION)
    val projectDescriptionTitle get() = get(StringKey.PROJECT_DESCRIPTION_TITLE)
    val projectDescriptionContent get() = get(StringKey.PROJECT_DESCRIPTION_CONTENT)
    val techStackTitle get() = get(StringKey.TECH_STACK_TITLE)
    val techStackContent get() = get(StringKey.TECH_STACK_CONTENT)
    val openSourceLicenseTitle get() = get(StringKey.OPEN_SOURCE_LICENSE_TITLE)
    val openSourceLicenseContent get() = get(StringKey.OPEN_SOURCE_LICENSE_CONTENT)

    val llmModelTitle get() = get(StringKey.LLM_MODEL_TITLE)
    val apiKeyTitle get() = get(StringKey.API_KEY_TITLE)
    val apiKeyPlaceholder get() = get(StringKey.API_KEY_PLACEHOLDER)
    val apiKeyLocalOnly get() = get(StringKey.API_KEY_LOCAL_ONLY)
    val saving get() = get(StringKey.SAVING)
    val saveConfig get() = get(StringKey.SAVE_CONFIG)

    val downloadingTab get() = get(StringKey.DOWNLOADING_TAB)
    val completedTab get() = get(StringKey.COMPLETED_TAB)
    val failedTab get() = get(StringKey.FAILED_TAB)

    val downloading get() = get(StringKey.DOWNLOADING)
    val completed get() = get(StringKey.COMPLETED)
    val storageSpace get() = get(StringKey.STORAGE_SPACE)

    val noDownloadTasks get() = get(StringKey.NO_DOWNLOAD_TASKS)
    val noCompletedTasks get() = get(StringKey.NO_COMPLETED_TASKS)
    val noFailedTasks get() = get(StringKey.NO_FAILED_TASKS)
    val noDownloadTasksHint get() = get(StringKey.NO_DOWNLOAD_TASKS_HINT)
    val noCompletedTasksHint get() = get(StringKey.NO_COMPLETED_TASKS_HINT)
    val noFailedTasksHint get() = get(StringKey.NO_FAILED_TASKS_HINT)

    val deleteDownloadTitle get() = get(StringKey.DELETE_DOWNLOAD_TITLE)
    val deleteDownloadMessage get() = get(StringKey.DELETE_DOWNLOAD_MESSAGE)

    val pause get() = get(StringKey.PAUSE)
    val resume get() = get(StringKey.RESUME)
    val play get() = get(StringKey.PLAY)
    val retry get() = get(StringKey.RETRY)

    val resourcePreview get() = get(StringKey.RESOURCE_PREVIEW)
    val parsingVideo get() = get(StringKey.PARSING_VIDEO)
    val videoPlay get() = get(StringKey.VIDEO_PLAY)
    val relatedResources get() = get(StringKey.RELATED_RESOURCES)
    val availableSourcesLabel get() = get(StringKey.AVAILABLE_SOURCES_LABEL)
    val selectSourceToDownload get() = get(StringKey.SELECT_SOURCE_TO_DOWNLOAD)
    val downloadTip get() = get(StringKey.DOWNLOAD_TIP)
    val noAvailableSources get() = get(StringKey.NO_AVAILABLE_SOURCES)
    val downloadable get() = get(StringKey.DOWNLOADABLE)
    val streamingFormat get() = get(StringKey.STREAMING_FORMAT)
    val unknownFormat get() = get(StringKey.UNKNOWN_FORMAT)
    val tryingFallback get() = get(StringKey.TRYING_FALLBACK)
    val playbackError get() = get(StringKey.PLAYBACK_ERROR)
    val noPlayableSource get() = get(StringKey.NO_PLAYABLE_SOURCE)

    val calculating get() = get(StringKey.CALCULATING)

    val languageSetting get() = get(StringKey.LANGUAGE_SETTING)
    val languageSwitch get() = get(StringKey.LANGUAGE_SWITCH)

    val deepseekApiKeyPlaceholder get() = get(StringKey.DEEPSEEK_API_KEY_PLACEHOLDER)
    val hunyuanApiKeyPlaceholder get() = get(StringKey.HUNYUAN_API_KEY_PLACEHOLDER)
    val deepseekV4Flash get() = get(StringKey.DEEPSEEK_V4_FLASH)
    val hunyuanLite get() = get(StringKey.HUNYUAN_LITE)

    val unknownSize get() = get(StringKey.UNKNOWN_SIZE)
    val timeHours get() = get(StringKey.TIME_HOURS)
    val timeMinutes get() = get(StringKey.TIME_MINUTES)
    val timeSeconds get() = get(StringKey.TIME_SECONDS)
    val streamingFormatDownloadError get() = get(StringKey.STREAMING_FORMAT_DOWNLOAD_ERROR)

    val playbackSpeed get() = get(StringKey.PLAYBACK_SPEED)
    
    val pictureInPicture get() = get(StringKey.PICTURE_IN_PICTURE)
    val pipNotSupported get() = get(StringKey.PIP_NOT_SUPPORTED)
    val pipEnter get() = get(StringKey.PIP_ENTER)

    val searchHistory get() = get(StringKey.SEARCH_HISTORY)
    val today get() = get(StringKey.TODAY)
    val yesterday get() = get(StringKey.YESTERDAY)
    val noSearchResult get() = get(StringKey.NO_SEARCH_RESULT)

    val abLoop get() = get(StringKey.AB_LOOP)
    val abLoopSetA get() = get(StringKey.AB_LOOP_SET_A)
    val abLoopSetB get() = get(StringKey.AB_LOOP_SET_B)
    val abLoopActive get() = get(StringKey.AB_LOOP_ACTIVE)
    val abLoopCleared get() = get(StringKey.AB_LOOP_CLEARED)
    val abLoopASet get() = get(StringKey.AB_LOOP_A_SET)
    val abLoopBSet get() = get(StringKey.AB_LOOP_B_SET)
    val abLoopANotSet get() = get(StringKey.AB_LOOP_A_NOT_SET)
    val abLoopBNotSet get() = get(StringKey.AB_LOOP_B_NOT_SET)

    fun s(key: String): String = get(key)
}

class DefaultStringProvider(
    private val translations: Map<Language, Map<String, String>>,
    override val currentLanguage: Language,
    private val fallbackLanguage: Language = Language.ZH
) : StringProvider {

    override fun get(key: String): String {
        return translations[currentLanguage]?.get(key)
            ?: translations[fallbackLanguage]?.get(key)
            ?: key
    }
}
