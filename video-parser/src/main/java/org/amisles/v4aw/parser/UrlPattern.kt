package org.amisles.v4aw.parser

data class UrlPattern(
    val host: String,
    val pathPrefix: String,
    val queryKeys: Set<String>,
    val fullPattern: String
) {
    fun matches(url: String): Boolean {
        if (fullPattern.isNotEmpty() && url.contains(fullPattern)) return true
        if (queryKeys.isNotEmpty()) {
            val hasPathMatch = pathPrefix.isEmpty() || url.contains(pathPrefix)
            val hasQueryMatch = queryKeys.any { key ->
                url.contains("?$key=") || url.contains("&$key=")
            }
            return hasPathMatch && hasQueryMatch
        }
        return pathPrefix.isNotEmpty() && url.contains(pathPrefix)
    }
}
