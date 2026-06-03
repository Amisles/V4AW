package org.amisles.v4aw.parser

import android.util.Log
import org.jsoup.nodes.Document
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoSourceExtractor @Inject constructor() {
    private val TAG = "VideoSourceExtractor"

    fun extractVideoSources(doc: Document, baseUrl: String?): Pair<List<String>, List<String>> {
        val videoSources = mutableSetOf<String>()
        val iframeUrls = mutableListOf<String>()

        doc.select(VideoParserConstants.VIDEO_TAG).forEach { video ->
            val src = video.attr(VideoParserConstants.SRC_ATTR)
            src.takeIf { it.isNotEmpty() }?.let { videoSources.add(it) }
            video.select(VideoParserConstants.SOURCE_TAG).forEach { source ->
                val sourceSrc = source.attr(VideoParserConstants.SRC_ATTR)
                sourceSrc.takeIf { it.isNotEmpty() }?.let { videoSources.add(it) }
            }
        }

        doc.select(VideoParserConstants.EMBED_TAG).forEach { embed ->
            val embedSrc = embed.attr(VideoParserConstants.SRC_ATTR)
            embedSrc.takeIf { it.isNotEmpty() }?.let { videoSources.add(it) }
        }

        doc.select("${VideoParserConstants.LINK_TAG}[${VideoParserConstants.REL_ATTR}='${VideoParserConstants.PRELOAD_VALUE}'][${VideoParserConstants.AS_ATTR}='${VideoParserConstants.VIDEO_VALUE}']").forEach { link ->
            val href = link.attr(VideoParserConstants.HREF_ATTR)
            href.takeIf { it.isNotEmpty() }?.let { videoSources.add(it) }
        }

        doc.select(VideoParserConstants.SCRIPT_TAG).forEach { script ->
            UrlUtils.extractUrlsFromScript(script.html()).forEach { videoSources.add(it) }
        }

        val dataAttrSelectors = "[${VideoParserConstants.DATA_SRC_ATTR}], [${VideoParserConstants.DATA_URL_ATTR}], [${VideoParserConstants.DATA_VIDEO_ATTR}]"
        doc.select(dataAttrSelectors).forEach { element ->
            element.attr(VideoParserConstants.DATA_SRC_ATTR).takeIf { it.isNotEmpty() }?.let { videoSources.add(it) }
            element.attr(VideoParserConstants.DATA_URL_ATTR).takeIf { it.isNotEmpty() }?.let { videoSources.add(it) }
            element.attr(VideoParserConstants.DATA_VIDEO_ATTR).takeIf { it.isNotEmpty() }?.let { videoSources.add(it) }
        }

        doc.select(VideoParserConstants.IFRAME_TAG).forEach { iframe ->
            val iframeSrc = iframe.attr(VideoParserConstants.SRC_ATTR)
            iframeSrc.takeIf { it.isNotEmpty() }?.let { src ->
                iframeUrls.add(src)
                UrlUtils.extractVideoUrlFromIframe(src)?.let { videoUrl ->
                    videoSources.add(videoUrl)
                }
            }
        }

        Log.i(TAG, "extractVideoSources: found ${videoSources.size} video sources")
        videoSources.forEachIndexed { index, src ->
            Log.i(TAG, "  [$index] ${src.take(200)}")
        }

        return Pair(videoSources.toList(), iframeUrls)
    }
}
