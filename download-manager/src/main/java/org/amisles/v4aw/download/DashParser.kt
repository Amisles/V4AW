package org.amisles.v4aw.download

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

data class DashManifest(
    val periods: List<DashPeriod> = emptyList()
)

data class DashPeriod(
    val adaptations: List<DashAdaptationSet> = emptyList()
)

data class DashAdaptationSet(
    val contentType: String = "",
    val mimeType: String = "",
    val representations: List<DashRepresentation> = emptyList()
)

data class DashRepresentation(
    val id: String = "",
    val bandwidth: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val mimeType: String = "",
    val codecs: String = "",
    val baseUrl: String = "",
    val segmentTemplate: DashSegmentTemplate? = null,
    val segmentList: DashSegmentList? = null
)

data class DashSegmentTemplate(
    val initialization: String = "",
    val media: String = "",
    val startNumber: Long = 1,
    val timescale: Long = 1,
    val duration: Long = 0,
    val timeline: List<DashTimelineEntry> = emptyList()
)

data class DashTimelineEntry(
    val t: Long = 0,
    val d: Long = 0,
    val r: Int = 0
)

data class DashSegmentList(
    val initialization: DashSegmentUrl? = null,
    val segments: List<DashSegmentUrl> = emptyList()
)

data class DashSegmentUrl(
    val media: String = "",
    val index: Int = 0
)

object DashParser {

    fun parse(content: String, baseUrl: String): DashManifest {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(content))

        var eventType = parser.eventType
        var periods = mutableListOf<DashPeriod>()
        var currentPeriod: DashPeriod? = null
        var currentAdaptation: DashAdaptationSet? = null
        var currentRepresentation: DashRepresentation? = null
        var currentSegmentTemplate: DashSegmentTemplate? = null
        var currentTimeline = mutableListOf<DashTimelineEntry>()
        var currentSegmentList: DashSegmentList? = null
        var currentSegmentUrls = mutableListOf<DashSegmentUrl>()
        var segmentIndex = 0
        var inTimeline = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "Period" -> {
                            currentPeriod = DashPeriod()
                        }
                        "AdaptationSet" -> {
                            val contentType = parser.getAttributeValue(null, "contentType") ?: ""
                            val mimeType = parser.getAttributeValue(null, "mimeType") ?: ""
                            currentAdaptation = DashAdaptationSet(
                                contentType = contentType,
                                mimeType = mimeType
                            )
                        }
                        "Representation" -> {
                            val id = parser.getAttributeValue(null, "id") ?: ""
                            val bandwidth = parser.getAttributeValue(null, "bandwidth")?.toLongOrNull() ?: 0
                            val width = parser.getAttributeValue(null, "width")?.toIntOrNull() ?: 0
                            val height = parser.getAttributeValue(null, "height")?.toIntOrNull() ?: 0
                            val mimeType = parser.getAttributeValue(null, "mimeType")
                                ?: currentAdaptation?.mimeType ?: ""
                            val codecs = parser.getAttributeValue(null, "codecs") ?: ""
                            currentRepresentation = DashRepresentation(
                                id = id,
                                bandwidth = bandwidth,
                                width = width,
                                height = height,
                                mimeType = mimeType,
                                codecs = codecs
                            )
                            currentSegmentTemplate = null
                            currentTimeline = mutableListOf()
                            currentSegmentList = null
                            currentSegmentUrls = mutableListOf()
                            segmentIndex = 0
                        }
                        "BaseURL" -> {
                            if (currentRepresentation != null) {
                                currentRepresentation = currentRepresentation!!.copy(
                                    baseUrl = M3u8Parser.resolveUrl(baseUrl, parser.nextText().trim())
                                )
                            }
                        }
                        "SegmentTemplate" -> {
                            val init = parser.getAttributeValue(null, "initialization") ?: ""
                            val media = parser.getAttributeValue(null, "media") ?: ""
                            val startNumber = parser.getAttributeValue(null, "startNumber")?.toLongOrNull() ?: 1
                            val timescale = parser.getAttributeValue(null, "timescale")?.toLongOrNull() ?: 1
                            val duration = parser.getAttributeValue(null, "duration")?.toLongOrNull() ?: 0
                            currentSegmentTemplate = DashSegmentTemplate(
                                initialization = init,
                                media = media,
                                startNumber = startNumber,
                                timescale = timescale,
                                duration = duration
                            )
                            inTimeline = false
                        }
                        "SegmentTimeline" -> {
                            inTimeline = true
                            currentTimeline = mutableListOf()
                        }
                        "S" -> {
                            if (inTimeline) {
                                val t = parser.getAttributeValue(null, "t")?.toLongOrNull() ?: 0
                                val d = parser.getAttributeValue(null, "d")?.toLongOrNull() ?: 0
                                val r = parser.getAttributeValue(null, "r")?.toIntOrNull() ?: 0
                                currentTimeline.add(DashTimelineEntry(t = t, d = d, r = r))
                            }
                        }
                        "SegmentList" -> {
                            currentSegmentList = DashSegmentList()
                            currentSegmentUrls = mutableListOf()
                            segmentIndex = 0
                        }
                        "Initialization" -> {
                            val source = parser.getAttributeValue(null, "source") ?: ""
                            if (currentSegmentList != null) {
                                currentSegmentList = currentSegmentList!!.copy(
                                    initialization = DashSegmentUrl(media = source, index = 0)
                                )
                            }
                        }
                        "SegmentURL" -> {
                            val media = parser.getAttributeValue(null, "media") ?: ""
                            currentSegmentUrls.add(DashSegmentUrl(media = media, index = segmentIndex++))
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "SegmentTimeline" -> {
                            inTimeline = false
                            if (currentSegmentTemplate != null) {
                                currentSegmentTemplate = currentSegmentTemplate!!.copy(
                                    timeline = currentTimeline.toList()
                                )
                            }
                        }
                        "SegmentList" -> {
                            if (currentSegmentList != null) {
                                currentSegmentList = currentSegmentList!!.copy(
                                    segments = currentSegmentUrls.toList()
                                )
                            }
                        }
                        "SegmentTemplate" -> {
                            if (currentRepresentation != null) {
                                currentRepresentation = currentRepresentation!!.copy(
                                    segmentTemplate = currentSegmentTemplate
                                )
                            }
                        }
                        "Representation" -> {
                            if (currentRepresentation != null) {
                                val rep = if (currentRepresentation!!.segmentList == null && currentSegmentList != null) {
                                    currentRepresentation!!.copy(segmentList = currentSegmentList)
                                } else {
                                    currentRepresentation!!
                                }
                                currentAdaptation = currentAdaptation?.copy(
                                    representations = currentAdaptation!!.representations + rep
                                )
                            }
                            currentRepresentation = null
                            currentSegmentTemplate = null
                            currentTimeline = mutableListOf()
                            currentSegmentList = null
                            currentSegmentUrls = mutableListOf()
                        }
                        "AdaptationSet" -> {
                            if (currentAdaptation != null) {
                                currentPeriod = currentPeriod?.copy(
                                    adaptations = currentPeriod!!.adaptations + currentAdaptation!!
                                )
                            }
                            currentAdaptation = null
                        }
                        "Period" -> {
                            if (currentPeriod != null) {
                                periods.add(currentPeriod!!)
                            }
                            currentPeriod = null
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return DashManifest(periods = periods)
    }

    fun getSegmentUrls(representation: DashRepresentation, baseUrl: String): List<String> {
        val segments = mutableListOf<String>()

        representation.segmentTemplate?.let { template ->
            if (template.timeline.isNotEmpty()) {
                var time = 0L
                var number = template.startNumber
                for (entry in template.timeline) {
                    var t = if (entry.t > 0) entry.t else time
                    val repeatCount = if (entry.r >= 0) entry.r + 1 else 1
                    for (i in 0 until repeatCount) {
                        val mediaUrl = template.media
                            .replace("\$Number\$", number.toString())
                            .replace("\$Time\$", t.toString())
                            .replace("\$Bandwidth\$", representation.bandwidth.toString())
                            .replace("\$RepresentationID\$", representation.id)
                        segments.add(M3u8Parser.resolveUrl(baseUrl, mediaUrl))
                        t += entry.d
                        number++
                    }
                    time = t
                }
            } else if (template.duration > 0) {
                var number = template.startNumber
                var time = 0L
                while (true) {
                    val mediaUrl = template.media
                        .replace("\$Number\$", number.toString())
                        .replace("\$Time\$", time.toString())
                        .replace("\$Bandwidth\$", representation.bandwidth.toString())
                        .replace("\$RepresentationID\$", representation.id)
                    segments.add(M3u8Parser.resolveUrl(baseUrl, mediaUrl))
                    time += template.duration
                    number++
                    if (segments.size >= 5000) break
                }
            }
        }

        representation.segmentList?.let { segList ->
            segList.segments.forEach { seg ->
                segments.add(M3u8Parser.resolveUrl(baseUrl, seg.media))
            }
        }

        if (segments.isEmpty() && representation.baseUrl.isNotEmpty()) {
            segments.add(representation.baseUrl)
        }

        return segments
    }

    fun getBestVideoRepresentation(adaptationSet: DashAdaptationSet): DashRepresentation? {
        return adaptationSet.representations
            .filter { it.mimeType.contains("video", ignoreCase = true) || it.height > 0 }
            .maxByOrNull { it.height * it.width.toLong() }
            ?: adaptationSet.representations.maxByOrNull { it.bandwidth }
    }

    fun getBestAudioRepresentation(adaptationSet: DashAdaptationSet): DashRepresentation? {
        return adaptationSet.representations
            .filter { it.mimeType.contains("audio", ignoreCase = true) || (it.width == 0 && it.height == 0) }
            .maxByOrNull { it.bandwidth }
            ?: adaptationSet.representations.maxByOrNull { it.bandwidth }
    }

    fun isVideoAdaptation(adaptationSet: DashAdaptationSet): Boolean {
        return adaptationSet.contentType.equals("video", ignoreCase = true) ||
                adaptationSet.mimeType.contains("video", ignoreCase = true) ||
                adaptationSet.representations.any { it.height > 0 || it.mimeType.contains("video", ignoreCase = true) }
    }

    fun isAudioAdaptation(adaptationSet: DashAdaptationSet): Boolean {
        return adaptationSet.contentType.equals("audio", ignoreCase = true) ||
                adaptationSet.mimeType.contains("audio", ignoreCase = true) ||
                adaptationSet.representations.any { it.height == 0 && it.mimeType.contains("audio", ignoreCase = true) }
    }
}
