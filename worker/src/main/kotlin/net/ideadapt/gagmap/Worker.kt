package net.ideadapt.gagmap

import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.InputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory


fun main() {
    val feedStream: InputStream = URI("https://www.geschichte.fm/feed/mp3").toURL().openStream()
    Files.copy(feedStream, Paths.get("./data/feed.xml"), StandardCopyOption.REPLACE_EXISTING)
    val feedXmlFile = File("./data/feed.xml")
    val document = parseXmlFile(feedXmlFile)
    val episodes = parseRssFeed(document)
    val db = File("data/episodes.jsonl")
    db.delete()
    episodes.forEach {
        val episodeJson = Json.encodeToString(it)
        db.appendText(episodeJson + "\n")
    }
}

fun parseXmlFile(file: File): Document {
    return try {
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
    } catch (e: Exception) {
        throw Exception("Unable to parse RSS feed XML", e)
    }
}

fun parseRssFeed(document: Document): MutableList<Episode> {
    val rssElement = document.documentElement
    if (rssElement.tagName != "rss") {
        throw IllegalArgumentException("Invalid RSS feed XML, missing 'rss' start tag.")
    }
    val channelElement = rssElement.getElementsByTagName("channel").item(0) as Element
    val items = channelElement.getElementsByTagName("item")
    val episodes = mutableListOf<Episode>()
    for (i in 0 until items.length) {
        val itemElement = items.item(i) as Element
        val title = itemElement.getSingleChildText("title")

        if (!title.startsWith("GAG")) continue // skip Feedback (FGAG) or Bonus / Extra episodes.

        val episodeNumber = itemElement.getSingleChildText("itunes:episode").toInt()
        val pubDate = itemElement.getSingleChildText("pubDate")
        val contentEncoded = itemElement.getSingleChildText("content:encoded")
        val description = itemElement.getSingleChildText("description")
        val durationInSeconds = itemElement.getSingleChildText("itunes:duration").toLong()
        val audioUrl = itemElement.getSingleChild("enclosure").attributes.getNamedItem("url").textContent

        val episodeLinks = extractEpisodeLinks(contentEncoded, episodeNumber)
        val descriptionNormalized = normalizeDescription(description)
        val temporalLinks = extractTemporalRefs(descriptionNormalized)

        val episode = Episode(
            id = episodeNumber,
            title = title.replace(Regex("GAG\\d\\d\\d?: "), ""),
            // Wed, 29 May 2024 07:00:00 +0000
            date = ZonedDateTime.parse(pubDate, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toKotlinInstant(),
            websiteUrl = URI("https://gadg.fm/$episodeNumber"),
            audioUrl = URI(audioUrl),
            durationInSeconds = durationInSeconds,
            episodeLinks = episodeLinks,
            description = descriptionNormalized,
            transcript = "",
            literature = emptyList(),
            temporalLinks = temporalLinks,
        )
        episodes.add(episode)
    }
    return episodes
}

private fun extractEpisodeLinks(contentEncoded: String, episodeNumber: Int): List<Int> {
    // link format changed from zs270 to gag271.
    // gadg.fm/{id} redirects to correct URL for given, unformatted episodeId.
    // gadg.fm/362
    // geschichte.fm/podcast/zs104
    // geschichte.fm/archiv/gag07
    val urlFormats = Regex("(gadg\\.fm/|geschichte\\.fm/podcast/zs|geschichte\\.fm/archiv/gag)(\\d\\d\\d?)")
    val episodeLinks = contentEncoded.takeIf { it.isNotEmpty() }?.let {
        urlFormats.findAll(it)
            .map { m ->
                m.groupValues[2].toInt()
            }
    }
        .orEmpty()
        .filter { it != episodeNumber }
        .toSortedSet()
        .toList()
    return episodeLinks
}

private fun normalizeDescription(description: String) = description
    .lines()
    .filter { it.isNotBlank() }
    // ad block in description field starts with "aus unserer werbung" headings (html or plain text or other markup)
    // literature or related episodes or other blocks after the description text start with Regex("// ?")
    .takeWhile { !it.contains("aus unserer werbung", ignoreCase = true) && !it.startsWith("//") }
    .map { line ->
        line
            .filter { it == ' ' || !it.isWhitespace() }
            .replace("  ", " ")
            .trim()
    }
    .joinToString(" ")

fun extractTemporalRefs(descriptionNormalized: String): List<TemporalRef> {
    // Examples:
    // 14. September 2022
    // September 2022
    // 19. Jahrhunderts?
    // Jahr 2022
    // 2000er Jahren?
    // 30er Jahren? des 20. (JH|Jahrhunderts?)
    val monthNames = "Januar|Februar|März|April|Mai|Juni|Juli|August|September|Oktober|November|Dezember"

    var startIdx = 0
    var done = false
    // TODO make while more nice
    val regexes = listOf(
        Regex("\\d\\d?\\. ($monthNames) \\d{1,4}( vdZw)?") to TemporalRef.Companion.Mode.Day,
        Regex("($monthNames) \\d{1,4}( vdZw)?") to TemporalRef.Companion.Mode.Month,
        Regex("\\d{1,4}er Jahren? des \\d\\d?\\. (JH|Jahrhunderts?)( vdZw)?") to TemporalRef.Companion.Mode.JzRelative,
        Regex("\\d\\d?\\. Jahrhunderts?( vdZw)?") to TemporalRef.Companion.Mode.Jh,
        Regex("Jahr \\d{1,4}( vdZw)?") to TemporalRef.Companion.Mode.J,
        Regex("\\d{1,4}er Jahren?( vdZw)?") to TemporalRef.Companion.Mode.JzAbsolute,
        // only 2 cases so far (episodes 118 and 158), both of them contain other date refs in their description.
        // Regex("\\d{1,2}er Jahren?( vdZw)?") to TemporalRef.Companion.Mode.JzImplicit,
    )
    val links = mutableListOf<TemporalRef>()
    while (!done) {
        var noMatch = true
        for (regex in regexes) {
            val match = regex.first.find(descriptionNormalized, startIdx)
            if (match != null) {
                noMatch = false
                startIdx = match.range.endInclusive
                links.add(TemporalRef(match.value, regex.second, match.value.endsWith("vdZw", ignoreCase = true)))
                break
            }
        }
        done = noMatch
    }

    return links
}

@Serializable
data class TemporalRef(val literal: String, val displayText: String, val start: Instant, val end: Instant) {

    constructor(literal: String, mode: Mode, vdzw: Boolean) : this(
        literal = literal,
        displayText = literal
            .replace("Jahr ", "")
            .replace("Jahrhunderts", "Jh.")
            .replace("Jahrhundert", "Jh.")
            .replace(" JH", " Jh."),
        start = getStart(literal, mode, vdzw),
        end = getEnd(literal, mode, vdzw),
    )

    companion object {

        private fun parseGermanDate(dateString: String): LocalDate {
            val formatter = DateTimeFormatter.ofPattern("d. MMMM u", Locale.GERMAN)
            return LocalDate.parse(dateString, formatter)
        }

        fun getStart(literal: String, mode: Mode, vdzw: Boolean): Instant =
            when (mode) {
                Mode.Day -> {
                        parseGermanDate(literal).atStartOfDay().toInstant(ZoneOffset.UTC)
                            .truncatedTo(ChronoUnit.SECONDS).toKotlinInstant()
                }

                Mode.Month -> {
                        parseGermanDate("1. $literal").atStartOfDay().toInstant(ZoneOffset.UTC)
                            .truncatedTo(ChronoUnit.SECONDS).toKotlinInstant()
                }

                Mode.Jh -> {
                    val century = literal.substringBefore(". Jahrhundert").toInt() * 100
                    if (vdzw) {
                        parseGermanDate("1. Januar -$century")
                    } else {
                        parseGermanDate("1. Januar ${century - 99}")
                    }.atStartOfDay().toInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).toKotlinInstant()
                }

                Mode.J -> {
                    val year = literal.lowercase().substringBefore(" vdzw").substringAfter("jahr ").toInt()
                    if (vdzw) {
                        parseGermanDate("1. Januar ${-year}")
                    } else {
                        parseGermanDate("1. Januar $year")
                    }.atStartOfDay().toInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).toKotlinInstant()
                }

                Mode.JzAbsolute -> {
                    val decade = literal.substringBefore("er Jahre").toInt()
                    if (vdzw) {
                        parseGermanDate("1. Januar ${-decade - 9}")
                    } else {
                        val fixedDecade = if (decade == 0) 1 else decade
                        parseGermanDate("1. Januar $fixedDecade")
                    }.atStartOfDay().toInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).toKotlinInstant()
                }

                Mode.JzRelative -> {
                    val century = literal.substringBefore(". Jahrhundert", missingDelimiterValue = "")
                        .ifEmpty { literal.substringBefore(". JH") }.substringAfter(" des ").toInt() * 100
                    val decade = literal.substringBefore("er Jahre").toInt()
                    if (vdzw) {
                        parseGermanDate("1. Januar ${-(decade + 9 + century - 100)}")
                    } else {
                        parseGermanDate("1. Januar ${decade + century - 100}")
                    }.atStartOfDay().toInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).toKotlinInstant()
                }
            }

        fun getEnd(literal: String, mode: Mode, vdzw: Boolean): Instant =
            when (mode) {
                Mode.Day -> {
                        parseGermanDate(literal).atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC)
                            .truncatedTo(ChronoUnit.SECONDS).toKotlinInstant()
                }

                Mode.Month -> {
                        parseGermanDate("1. $literal").atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC)
                            .truncatedTo(ChronoUnit.SECONDS).toKotlinInstant()
                }

                Mode.Jh -> {
                    val century = literal.substringBefore(". Jahrhundert").toInt() * 100
                    if (vdzw) {
                        parseGermanDate("31. Dezember ${-century + 99}")
                    } else {
                        parseGermanDate("31. Dezember $century")
                    }.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).toKotlinInstant()
                }

                Mode.J -> {
                    val year = literal.lowercase().substringBefore(" vdzw").substringAfter("jahr ").toInt()
                    if (vdzw) {
                        parseGermanDate("31. Dezember ${-year}")
                    } else {
                        parseGermanDate("31. Dezember $year")
                    }.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).toKotlinInstant()
                }

                Mode.JzAbsolute -> {
                    val decade = literal.substringBefore("er Jahre").toInt()
                    if (vdzw) {
                        val fixedDecade = if (decade == 0) 1 else decade
                        parseGermanDate("31. Dezember ${-fixedDecade}")
                    } else {
                        parseGermanDate("31. Dezember ${decade + 9}")
                    }.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).toKotlinInstant()
                }

                Mode.JzRelative -> {
                    val century = literal.substringBefore(". Jahrhundert", missingDelimiterValue = "")
                        .ifEmpty { literal.substringBefore(". JH") }.substringAfter(" des ").toInt() * 100
                    val decade = literal.substringBefore("er Jahre").toInt()
                    if (vdzw) {
                        val fixedDecade = if (decade == 0) 1 else decade
                        parseGermanDate("31. Dezember ${-(century - 100 + fixedDecade)}")
                    } else {
                        parseGermanDate("31. Dezember ${century - 100 + decade + 9}")
                    }.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).toKotlinInstant()
                }
            }


        enum class Mode {
            Day, Month, J, JzAbsolute, JzRelative, Jh
        }
    }
}

fun Element.getSingleChildText(tagName: String): String = this.getSingleChild(tagName).textContent

fun Element.getSingleChild(tagName: String): Node {
    val nodeList = this.getElementsByTagName(tagName)
    if (nodeList.length > 0) {
        return nodeList.item(0)
    }
    throw IllegalArgumentException("'$tagName' does not exist in '${this.tagName}'")
}

@Serializable
data class Episode(
    val id: Int,
    val title: String,
    val date: Instant,
    val durationInSeconds: Long,
    @Serializable(with = URISerializer::class)
    val websiteUrl: URI,
    @Serializable(with = URISerializer::class)
    val audioUrl: URI,
    val transcript: String,
    val description: String,
    val episodeLinks: List<Int>,
    val temporalLinks: List<TemporalRef>,
    val literature: List<String>,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = URI::class)
object URISerializer : KSerializer<URI> {
    override fun deserialize(decoder: Decoder): URI {
        return URI.create(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: URI) {
        encoder.encodeString(value.toString())
    }
}
