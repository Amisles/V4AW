# V4AW - Video Without Ads

[![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-0095D5?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpack-compose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-GPL%20v3-blue.svg?style=for-the-badge)](https://www.gnu.org/licenses/gpl-3.0)

A modern Android application that extracts and plays videos from websites without ads, featuring WebView-based content extraction, optional LLM analysis, and a beautiful Material Design 3 UI.

---

## ✨ Features

- 🌐 **WebView-based Content Extraction** - Bypasses anti-bot mechanisms by using a real WebView
- 🎬 **Automatic Video Source Detection** - Finds video tags, iframes, and network-captured sources
- � **Browse Mode** - Use resource preview as video website app, browse and search websites
- 🔧 **Custom Parse Rules** - Configure custom parsing rules for specific websites
- � **Ad Blocking** - Network-level ad blocking using request interception
- 📝 **LLM Integration** - Optional DeepSeek/Tencent Hunyuan analysis for better video detection
- 💾 **History Tracking** - Auto-saves browsing history for quick re-access
- 📥 **Video Downloader** - Supports downloading videos for offline viewing
- 🎨 **Modern UI** - Material Design 3 with Jetpack Compose
- 📱 **Clean Architecture** - MVVM with Clean Architecture principles
- 🔄 **Multiple Video Formats** - Supports HLS (.m3u8), DASH (.mpd), MP4, WebM, and more
- 🌍 **Internationalization** - Built-in i18n support with English and Chinese translations, auto-detects system language

---

## 🏗️ Architecture

V4AW follows **Clean Architecture** with **Modular Design** and clear separation of concerns:

```
   ┌────────────────────────────────────────────────────────────────────────────┐
   │          App Module (UI Layer + Domain Layer + App-specific Logic)         │
   └────────────────────────────────────────────────────────────────────────────┘
                                         │
        ┌────────────────────────────────┼──────────────────────────────────┐
        │              │                 │                │                 │
        ▼              ▼                 ▼                ▼                 ▼
┌────────────┐┌───────────────┐ ┌───────────────┐ ┌──────────────┐┌────────────────┐
│ Core Model ││ Video Parser  │ │WebView Manager│ │ Video Player ││Download Manager│
│  Library   ││  Library      │ │  Library      │ │  Library     ││    Library     │
└────────────┘└───────────────┘ └───────────────┘ └──────────────┘└────────────────┘                 
```

### Modules Explained

- **app**: Main application module containing UI screens, ViewModels, navigation, and use cases
- **core-model**: Shared domain models and data entities used across all modules
- **core-i18n**: Internationalization library with multi-language support
- **video-parser**: HTML/video source parsing library using Jsoup
- **webview-manager**: WebView management and content extraction library
- **download-manager**: Video download management library
- **video-player**: Core video playback library using Media3 (ExoPlayer)

### Architecture Layers

- **UI Layer**: Jetpack Compose screens, ViewModels, and navigation (in `app` module)
- **Domain Layer**: Business logic, use cases, and repository contracts (in `app` module)
- **Data Layer**: Data sources, repositories, and external service integrations (in `app` and library modules)

---

## 🚀 Getting Started

### Prerequisites

- **Android Studio** Hedgehog (2023.1.1) or later
- **JDK** 11 or later
- **Android SDK** - minSdk 30, targetSdk 36

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/Amisles/V4AW.git
   cd V4AW
   ```

2. **Open in Android Studio**
   - Open Android Studio
   - Select "Open an Existing Project"
   - Choose the cloned `V4AW` directory

3. **Sync Gradle**
   - Click "Sync Now" in the banner to download dependencies

4. **(Optional) Configure LLM API**
   - Open the app and go to Profile → LLM Settings
   - Select your provider (DeepSeek or Tencent Hunyuan)
   - Enter your API key

5. **Build and Run**
   - Select an emulator or connected device
   - Click "Run" or press `Shift+F10`

---

## 📖 How to Use

### Basic Usage

1. **Enter URL**
   - On the main screen, enter a website URL containing video content
   - Example: `https://example.com/video-page`

2. **Analyze Video**
   - Tap "Analyze Video"
   - The app loads the page in a hidden WebView, blocks ads, and detects video sources

3. **Play Video**
   - If video sources are found, the player opens automatically
   - Use ExoPlayer controls for playback
   - Toggle fullscreen for immersive viewing

4. **Download Videos**
   - Tap the download button in the player
   - Select a video source (green = downloadable)
   - Monitor progress in the Downloads tab

5. **View History**
   - Tap History in the bottom navigation
   - Quick re-analyze previous videos

### Browse Mode

1. **Enter Website Home Page**
   - On the main screen, enter a video website home page URL
   - Example: `https://example.com`

2. **Browse and Search**
   - Use the built-in browser to navigate the website
   - Tap search endpoints to search for videos
   - Tap video thumbnails to play videos

3. **Manage Custom Parse Rules**
   - Go to Profile → Site Rules
   - Add/edit/delete custom parsing rules for specific websites
   - Configure video source extraction, video entry extraction, search endpoint extraction, and WebView behavior

---

## 📦 Project Structure

```
V4AW/
├── app/                            # Main application module
│   ├── src/main/java/org/amisles/v4aw/
│   │   ├── V4awApplication.kt      # Application class with Hilt
│   │   ├── MainActivity.kt         # Main activity (single activity)
│   │   ├── data/                   # App-specific data layer
│   │   │   ├── di/
│   │   │   │   └── AppModule.kt    # Hilt dependency injection module
│   │   │   ├── llm/
│   │   │   │   └── LlmClient.kt    # LLM API client (DeepSeek, Hunyuan)
│   │   │   ├── local/              # Local data sources
│   │   │   │   ├── dao/
│   │   │   │   │   └── SiteRuleDao.kt  # Site rules DAO
│   │   │   │   └── database/
│   │   │   │       ├── AppDatabase.kt  # Room database
│   │   │   │       └── SiteRuleConverters.kt  # Type converters
│   │   │   ├── repository/
│   │   │   │   ├── HistoryRepositoryImpl.kt
│   │   │   │   └── VideoRepositoryImpl.kt
│   │   ├── domain/                 # Domain layer (use cases, contracts)
│   │   │   ├── repository/
│   │   │   │   ├── HistoryRepository.kt
│   │   │   │   └── VideoRepository.kt
│   │   │   └── usecase/
│   │   │       ├── GetVideoSourceUseCase.kt
│   │   │       ├── HistoryUseCase.kt
│   │   │       ├── MatchSiteRuleUseCase.kt  # Match site rules
│   │   │       └── ParseVideoUrlUseCase.kt
│   │   ├── i18n/                   # Internationalization support
│   │   └── ui/                     # UI layer (Jetpack Compose)
│   │       ├── components/
│   │       ├── navigation/
│   │       ├── screen/
│   │       │   └── siterules/      # Site rules management screens
│   │       └── theme/
│   └── build.gradle.kts
│
├── core-model/                     # Shared models library module
│   └── src/main/java/org/amisles/v4aw/model/
│       ├── DownloadInfo.kt
│       ├── HistoryItem.kt
│       ├── LlmConfig.kt
│       ├── ParseResult.kt
│       ├── SiteRule.kt            # Custom site parsing rules
│       ├── VideoEntry.kt
│       └── VideoInfo.kt
│
├── core-i18n/                      # Internationalization library module
│   └── src/main/java/org/amisles/v4aw/i18n/
│       ├── translations/
│       │   ├── Translations.kt
│       │   ├── en.kt
│       │   └── zh.kt
│       ├── I18nProvider.kt
│       ├── Language.kt
│       └── StringProvider.kt
│
├── video-parser/                   # Video parsing library module
│   └── src/main/java/org/amisles/v4aw/parser/
│       ├── RuleBasedExtractor.kt  # Rule-based extraction engine
│       ├── SearchEndpointExtractor.kt  # Search endpoint extraction
│       └── VideoParser.kt
│
├── webview-manager/                # WebView management library module
│   └── src/main/java/org/amisles/v4aw/webview/
│       └── WebViewManager.kt
│
├── download-manager/               # Download management library module
│   └── src/main/java/org/amisles/v4aw/download/
│       ├── DownloadDao.kt
│       └── DownloadManager.kt
│
├── video-player/                   # Video player library module
│   └── src/main/java/org/amisles/v4aw/player/
│       └── VideoPlayer.kt
│
├── gradle/libs.versions.toml       # Gradle version catalog
├── build.gradle.kts                # Root build script
└── settings.gradle.kts             # Project settings
```

---

## 🛠️ Technologies Used

| Category | Technology |
|----------|-----------|
| **UI Framework** | Jetpack Compose + Material Design 3 |
| **Navigation** | Jetpack Navigation Compose |
| **Dependency Injection** | Hilt |
| **Database** | Room |
| **Video Playback** | Media3 (ExoPlayer) |
| **HTML Parsing** | Jsoup |
| **Networking** | OkHttp |
| **Async** | Kotlin Coroutines + Flow |
| **Serialization** | kotlinx.serialization |
| **Preferences** | DataStore |
| **Internationalization** | Custom i18n system |

---

## 🔧 Key Components

### Core Modules

#### core-model
Shared domain models and data entities:
- `VideoInfo` - Video metadata and sources
- `VideoEntry` - Related video entry
- `HistoryItem` - Browsing history item
- `ParseResult` - Video parsing result wrapper
- `DownloadInfo` - Download state info
- `LlmConfig` - LLM configuration
- `SiteRule` - Custom site parsing rules for video sources, video entries, and search endpoints

#### core-i18n
Internationalization support:
- Multi-language support (English, Chinese)
- Type-safe string resources
- Runtime language switching
- Auto-detects system language on first launch
- Easy to extend with new languages

### Library Modules

#### WebViewManager (`webview-manager` module)
Manages a hidden WebView instance that:
- Loads web pages and waits for content
- Intercepts network requests to block ads
- Captures video URLs from network traffic
- Injects JavaScript to extract HTML content
- Configurable behavior via custom rules (delay, user agent, ad blocking, scrolling, clicking, script injection)

#### VideoParser (`video-parser` module)
Uses Jsoup to parse HTML and extract:
- `<video>` tags and `<source>` elements
- `<iframe>` embeds
- JavaScript-embedded video URLs
- Validates and prioritizes video sources
- Rule-based extraction engine for custom site rules
- Search endpoint extraction

#### VideoPlayer (`video-player` module)
Core video playback library that:
- Manages ExoPlayer instance
- Supports multiple formats (MP4, WebM, HLS, DASH)
- Provides StateFlow-based state management
- Handles playback errors and fallbacks

#### DownloadManager (`download-manager` module)
Handles video downloads with:
- Progress tracking
- Pause/resume support
- Background downloads
- Room-persisted download state

### App Components

#### LlmClient (Optional)
Supports multiple LLM providers:
- DeepSeek (default)
- Tencent Hunyuan
- Analyzes webpage content to find hidden video sources
- Fallback option when automatic parsing fails

---

## 🤝 Contributing

Contributions are welcome! Here's how you can help:

1. **Fork the repository**
   ```bash
   git fork https://github.com/Amisles/V4AW
   ```

2. **Create a feature branch**
   ```bash
   git checkout -b feature/AmazingFeature
   ```

3. **Commit your changes**
   ```bash
   git commit -m 'Add some AmazingFeature'
   ```

4. **Push to the branch**
   ```bash
   git push origin feature/AmazingFeature
   ```

5. **Open a Pull Request**
   - Go to the [Pull Requests](https://github.com/Amisles/V4AW/pulls) page
   - Click "New Pull Request"
   - Describe your changes and submit

### Development Guidelines

- Follow the existing code style and architecture
- Write clear, maintainable code with proper comments
- Test your changes thoroughly before submitting
- Update documentation as needed
- Ensure all code is in English (comments, variable names, etc.)

---

## ⚠️ Important Notes

- This app is for **educational purposes only**
- Respect website terms of service and `robots.txt`
- Some websites may have DRM or advanced anti-scraping measures
- LLM integration requires an API key and may incur costs
- This project does not condone piracy or copyright infringement

---

## 🔍 Troubleshooting

### Video Not Found?
- Try a different website
- Ensure the video is publicly accessible
- Some streaming services use proprietary protocols
- Enable LLM analysis for better detection

### Build Errors?
- Verify you're using JDK 11 or later
- Try "File → Invalidate Caches / Restart" in Android Studio
- Check that all dependencies are synced properly
- Make sure you have the required Android SDK versions

### Download Issues?
- Check network connection
- Verify video source is directly accessible (not streaming-only)
- .m3u8 and .mpd formats cannot be downloaded directly

---

## 📄 License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- [Jetpack Compose](https://developer.android.com/jetpack/compose) - Modern Android UI
- [Media3](https://developer.android.com/media/media3) - Video playback
- [Jsoup](https://jsoup.org/) - HTML parsing
- [Hilt](https://dagger.dev/hilt/) - Dependency injection
- [Material Design 3](https://m3.material.io/) - Design system

---

## 📊 Project Status

![GitHub stars](https://img.shields.io/github/stars/Amisles/V4AW?style=social)
![GitHub forks](https://img.shields.io/github/forks/Amisles/V4AW?style=social)
![GitHub issues](https://img.shields.io/github/issues/Amisles/V4AW)
![GitHub license](https://img.shields.io/github/license/Amisles/V4AW)

---

## 📞 Contact

- **Project Link**: [https://github.com/Amisles/V4AW](https://github.com/Amisles/V4AW)
- **Issues**: [GitHub Issues](https://github.com/Amisles/V4AW/issues)
- **Releases**: [GitHub Releases](https://github.com/Amisles/V4AW/releases)

---

**Made with ❤️ by [Amisles](https://github.com/Amisles)**
