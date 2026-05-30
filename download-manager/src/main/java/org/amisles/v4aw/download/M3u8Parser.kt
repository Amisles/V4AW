package org.amisles.v4aw.download

data class M3u8Playlist(
    val isMaster: Boolean = false,
    val variants: List<M3u8Variant> = emptyList(),
    val segments: List<M3u8Segment> = emptyList(),
    val targetDuration: Int = 10,
    val encryption: M3u8Encryption? = null
)

data class M3u8Variant(
    val bandwidth: Long = 0,
    val resolution: String? = null,
    val url: String = ""
)

data class M3u8Segment(
    val index: Int = 0,
    val url: String = "",
    val duration: Float = 0f,
    val encryption: M3u8Encryption? = null
)

data class M3u8Encryption(
    val method: String = "NONE",
    val keyUrl: String = "",
    val iv: String? = null
)

object M3u8Parser {

    fun parse(content: String, baseUrl: String): M3u8Playlist {
        val lines = content.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (lines.isEmpty() || lines[0] != "#EXTM3U") {
            throw IllegalArgumentException("Invalid M3U8 format: missing #EXTM3U header")
        }

        val isMaster = lines.any { it.startsWith("#EXT-X-STREAM-INF") }

        return if (isMaster) {
            parseMasterPlaylist(lines, baseUrl)
        } else {
            parseMediaPlaylist(lines, baseUrl)
        }
    }

    private fun parseMasterPlaylist(lines: List<String>, baseUrl: String): M3u8Playlist {
        val variants = mutableListOf<M3u8Variant>()
        var currentBandwidth: Long = 0
        var currentResolution: String? = null

        for (line in lines) {
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                val attrs = parseAttributes(line.substringAfter("#EXT-X-STREAM-INF:"))
                currentBandwidth = attrs["BANDWIDTH"]?.toLongOrNull() ?: 0
                currentResolution = attrs["RESOLUTION"]
            } else if (!line.startsWith("#") && line.isNotEmpty()) {
                val url = resolveUrl(baseUrl, line)
                variants.add(M3u8Variant(
                    bandwidth = currentBandwidth,
                    resolution = currentResolution,
                    url = url
                ))
                currentBandwidth = 0
                currentResolution = null
            }
        }

        return M3u8Playlist(
            isMaster = true,
            variants = variants.sortedByDescending { it.bandwidth }
        )
    }

    private fun parseMediaPlaylist(lines: List<String>, baseUrl: String): M3u8Playlist {
        val segments = mutableListOf<M3u8Segment>()
        var targetDuration = 10
        var currentDuration = 0f
        var currentEncryption: M3u8Encryption? = null
        var segmentIndex = 0

        for (line in lines) {
            when {
                line.startsWith("#EXT-X-TARGETDURATION:") -> {
                    targetDuration = line.substringAfter(":").toIntOrNull() ?: 10
                }
                line.startsWith("#EXT-X-KEY:") -> {
                    currentEncryption = parseEncryption(line.substringAfter("#EXT-X-KEY:"), baseUrl)
                }
                line.startsWith("#EXTINF:") -> {
                    currentDuration = line.substringAfter(":")
                        .substringBefore(",")
                        .toFloatOrNull() ?: 0f
                }
                !line.startsWith("#") && line.isNotEmpty() -> {
                    val url = resolveUrl(baseUrl, line)
                    segments.add(M3u8Segment(
                        index = segmentIndex++,
                        url = url,
                        duration = currentDuration,
                        encryption = currentEncryption
                    ))
                    currentDuration = 0f
                }
            }
        }

        return M3u8Playlist(
            isMaster = false,
            segments = segments,
            targetDuration = targetDuration,
            encryption = segments.firstOrNull()?.encryption
        )
    }

    private fun parseAttributes(attrString: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val regex = Regex("""([A-Z0-9-]+)=("(?:[^"\\]|\\.)*"|[^,]+)""")
        regex.findAll(attrString).forEach { match ->
            val key = match.groupValues[1]
            var value = match.groupValues[2]
            if (value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length - 1)
            }
            result[key] = value
        }
        return result
    }

    private fun parseEncryption(attrString: String, baseUrl: String): M3u8Encryption? {
        val attrs = parseAttributes(attrString)
        val method = attrs["METHOD"] ?: return null
        if (method == "NONE") return null

        return M3u8Encryption(
            method = method,
            keyUrl = attrs["URI"]?.let { resolveUrl(baseUrl, it) } ?: "",
            iv = attrs["IV"]
        )
    }

    fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
            return relativeUrl
        }

        if (baseUrl.isEmpty()) return relativeUrl

        val baseUrlWithoutQuery = baseUrl.substringBefore("?")
        val baseUri = baseUrlWithoutQuery.substringBeforeLast("/") + "/"

        return when {
            relativeUrl.startsWith("//") -> {
                val scheme = baseUrl.substringBefore("://")
                "$scheme:$relativeUrl"
            }
            relativeUrl.startsWith("/") -> {
                val scheme = baseUrl.substringBefore("://")
                val afterScheme = baseUrl.substringAfter("://")
                val hostPort = afterScheme.substringBefore("/").substringBefore("?")
                "$scheme://$hostPort$relativeUrl"
            }
            else -> baseUri + relativeUrl
        }
    }

    fun getBestVariant(playlist: M3u8Playlist): M3u8Variant? {
        return playlist.variants.maxByOrNull { it.bandwidth }
    }
}
