package org.amisles.v4aw.parser

object VideoParserConstants {
    const val TAG = "VideoParser"
    const val MAX_HTML_LOG_LENGTH = 150
    const val MAX_VIDEO_ENTRIES = 20
    const val MIN_GROUP_SIZE_FOR_HEURISTIC = 3
    const val UNICODE_REGEX_PATTERN = """\\u([0-9a-fA-F]{4})"""

    // HTML Tags
    const val VIDEO_TAG = "video"
    const val SOURCE_TAG = "source"
    const val EMBED_TAG = "embed"
    const val LINK_TAG = "link"
    const val SCRIPT_TAG = "script"
    const val IFRAME_TAG = "iframe"
    const val ANCHOR_TAG = "a"
    const val DIV_TAG = "div"
    const val LI_TAG = "li"
    const val ARTICLE_TAG = "article"
    const val IMG_TAG = "img"

    // HTML Attributes
    const val SRC_ATTR = "src"
    const val DATA_SRC_ATTR = "data-src"
    const val DATA_URL_ATTR = "data-url"
    const val DATA_VIDEO_ATTR = "data-video"
    const val TITLE_ATTR = "title"
    const val ALT_ATTR = "alt"
    const val HREF_ATTR = "href"
    const val REL_ATTR = "rel"
    const val CLASS_ATTR = "class"

    // Attribute Values
    const val PRELOAD_VALUE = "preload"
    const val AS_ATTR = "as"
    const val VIDEO_VALUE = "video"

    // Strings
    const val UNKNOWN_VIDEO_TITLE = "Unknown Video"
    const val EMPTY_STRING = ""
    const val NEWLINE = "\n"
    const val TAB = "\t"
    const val CARRIAGE_RETURN = "\r"
    const val SINGLE_QUOTE_ESCAPED = "\\'"
    const val DOUBLE_QUOTE_ESCAPED = "\\\""
    const val BACKSLASH_ESCAPED = "\\\\"
    const val SINGLE_QUOTE = "'"
    const val DOUBLE_QUOTE = "\""
    const val BACKSLASH = "\\"
    const val HASH_PREFIX = "#"
    const val JAVASCRIPT_PREFIX = "javascript:"
    const val MAILTO_PREFIX = "mailto:"
    const val HTTPS_PREFIX = "https:"
    const val HTTP_PROTOCOL = "http"
    const val HTTPS_PROTOCOL = "https"

    // Extensions
    const val CSS_EXTENSION = ".css"
    const val JS_EXTENSION = ".js"
    const val XML_EXTENSION = ".xml"
    const val JPG_EXTENSION = ".jpg"
    const val PNG_EXTENSION = ".png"
    const val GIF_EXTENSION = ".gif"
    const val M3U8_EXTENSION = ".m3u8"
    const val MP4_EXTENSION = ".mp4"
    const val WEBM_EXTENSION = ".webm"
    const val MPD_EXTENSION = ".mpd"
    const val FLV_EXTENSION = ".flv"
    const val MOV_EXTENSION = ".mov"
    const val TS_EXTENSION = ".ts"
    const val M4V_EXTENSION = ".m4v"

    // Keywords
    const val VIDEO_INDICATOR = "video"
    const val HLS_INDICATOR = "hls"
    const val DASH_INDICATOR = "dash"
    const val MEDIA_INDICATOR = "media"
    const val STREAM_INDICATOR = "stream"
    const val PLAY_INDICATOR = "play"
    const val VOD_INDICATOR = "vod"
    const val CDN_INDICATOR = "cdn"
    const val PLAYER_HTML = "player.html"

    // URL Params
    const val URL_PARAM = "url"
    const val SRC_PARAM = "src"
    const val VIDEO_PARAM = "video"
    const val VIDEO_URL_PARAM = "video_url"
    const val PLAY_URL_PARAM = "play_url"
    const val SOURCE_PARAM = "source"
    const val LINK_PARAM = "link"
    const val DOT = "."
    const val QUESTION_MARK = "?"
    const val AMPERSAND = "&"
    const val EQUALS_SIGN = "="
    const val SLASH = "/"
    const val VERTICAL_BAR = "|"
    const val UTF_8 = "UTF-8"

    val VIDEO_ID_PARAMS = setOf(
        "v", "id", "vid", "video_id", "video", "ep", "episode",
        "watch", "play", "sid", "eid", "mid", "oid", "aid",
        "no", "num", "code", "key", "slug", "uid"
    )

    val IGNORE_PARAMS = setOf(
        "page", "sort", "ref", "lang", "theme", "mode", "q",
        "search", "tab", "view", "layout", "style", "type",
        "utm_source", "utm_medium", "utm_campaign", "utm_content",
        "from", "source", "channel", "category", "tag", "filter"
    )

    val VIDEO_EXTENSIONS_PRIORITY = listOf(
        M3U8_EXTENSION, MP4_EXTENSION, WEBM_EXTENSION,
        MPD_EXTENSION, FLV_EXTENSION, MOV_EXTENSION, TS_EXTENSION
    )

    val VALID_VIDEO_EXTENSIONS = listOf(
        MP4_EXTENSION, WEBM_EXTENSION, M3U8_EXTENSION,
        MPD_EXTENSION, FLV_EXTENSION, MOV_EXTENSION,
        TS_EXTENSION, M4V_EXTENSION
    )

    val AD_KEYWORDS = listOf(
        "ad.", "ads.", "advertise", "advertising", "tracking", "analytics",
        "beacon", "ping", "count", "stat", "pixel", "impression",
        "sharethis", "bluetrafficstream", "stripchat",
        "pop", "popup", "banner", "promo", "sponsor"
    )

    val SEARCH_URL_PATTERN = Regex("""['"`](https?://[^'"`\s]*(?:/search|/api/search|/api/query|/s\?)[^'"`\s]*)['"`]""")
    val SEARCH_URL_ASSIGNMENT_PATTERN = Regex("""url\s*[:=]\s*['"`]([^'"`\s]*(?:search|query)[^'"`\s]*)['"`]""")
    val SEARCH_GET_PATTERN = Regex("""\.get\(\s*['"`]([^'"`\s]*(?:search|query)[^'"`\s]*)['"`]""")
}
