package org.amisles.v4aw.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser

object HtmlUtils {
    data class ParsedPage(val doc: Document, val title: String)

    fun parseDocument(decodedHtml: String): ParsedPage {
        val doc = Jsoup.parse(decodedHtml)
        val title = doc.select("title").text().ifEmpty { VideoParserConstants.UNKNOWN_VIDEO_TITLE }
        return ParsedPage(doc, title)
    }

    fun decodeHtml(html: String): String {
        val sb = StringBuilder(html.length)
        var i = 0
        val n = html.length

        val unicodeRegex = Regex(VideoParserConstants.UNICODE_REGEX_PATTERN)
        var lastAppend = 0

        unicodeRegex.findAll(html).forEach { match ->
            sb.append(html.substring(lastAppend, match.range.first))
            sb.append(Integer.parseInt(match.groupValues[1], 16).toChar())
            lastAppend = match.range.last + 1
        }

        if (lastAppend < n) {
            sb.append(html.substring(lastAppend))
        }

        val intermediate = if (lastAppend == 0) html else sb.toString()

        val finalSb = StringBuilder(intermediate.length)
        i = 0
        while (i < intermediate.length) {
            when {
                i < intermediate.length - 1 && intermediate[i] == '\\' -> {
                    when (intermediate[i + 1]) {
                        'n' -> {
                            finalSb.append(VideoParserConstants.NEWLINE)
                            i += 2
                        }
                        't' -> {
                            finalSb.append(VideoParserConstants.TAB)
                            i += 2
                        }
                        'r' -> {
                            finalSb.append(VideoParserConstants.CARRIAGE_RETURN)
                            i += 2
                        }
                        '\'' -> {
                            finalSb.append(VideoParserConstants.SINGLE_QUOTE)
                            i += 2
                        }
                        '"' -> {
                            finalSb.append(VideoParserConstants.DOUBLE_QUOTE)
                            i += 2
                        }
                        '\\' -> {
                            finalSb.append(VideoParserConstants.BACKSLASH)
                            i += 2
                        }
                        else -> {
                            finalSb.append(intermediate[i])
                            i++
                        }
                    }
                }
                else -> {
                    finalSb.append(intermediate[i])
                    i++
                }
            }
        }

        return Parser.unescapeEntities(finalSb.toString(), false)
    }
}
