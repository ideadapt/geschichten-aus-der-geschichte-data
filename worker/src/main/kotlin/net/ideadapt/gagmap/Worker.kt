package net.ideadapt.gagmap

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
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
import java.util.zip.CRC32
import javax.xml.parsers.DocumentBuilderFactory

private val logger = LoggerFactory.getLogger("net.ideadapt.gagmap")

fun main() {
    val episodeDumpFile = File("data/episodes.jsonl")
    episodeDumpFile.createNewFile() // create if not exists
    val existingEpisodes =
        episodeDumpFile
            .readLines()
            .map { Json.decodeFromString<Episode>(it) }
            .associateBy { it.id }
            .toMutableMap()

    val feedStream: InputStream = URI("https://www.geschichte.fm/feed/mp3").toURL().openStream()
    Files.copy(feedStream, Paths.get("data/feed.xml"), StandardCopyOption.REPLACE_EXISTING)
    val feedXmlFile = File("data/feed.xml")
    val feedXmlDocument = parseXmlFile(feedXmlFile)
    val xmlEpisodes = getEpisodeElements(feedXmlDocument)

    // create episode objects with only information contained in the xml text.
    // this is elementary + fast.
    xmlEpisodes.forEach { xmlEpisode ->
        val xmlEpisodeChecksum = xmlEpisode.value.textContent.checksum()
        val maybeExistingEpisode = existingEpisodes[xmlEpisode.key]
        if (maybeExistingEpisode == null || xmlEpisodeChecksum != maybeExistingEpisode.checksum) {
            logger.info("Extract metadata from XML text for episode ${xmlEpisode.key}")
            val episode = parseXmlEpisode(xmlEpisode.value)
            existingEpisodes[xmlEpisode.key] = episode
        }
    }
    dumpEpisodes(episodeDumpFile, existingEpisodes)

    // try to find / extract more data using more expensive tooling, such as AI or other GAG metadata dumps.
    xmlEpisodes.keys
        .map { existingEpisodes[it]!! }
        .filter { it.locations == null }
        .windowed(size = 10, step = 10, partialWindows = true)
        .forEach { episodes ->
            logger.info("Extracting locations for episodes ${episodes.joinToString { it.id.toString() }}.")
            runBlocking {
                val aiClient = AiClient()
                val locations = aiClient.extractGeoLocations(episodes.associate { it.id to it.description })
                logger.info("Extracted locations ${locations.map { it.key.toString() + ": " + it.value }}.")
                episodes.forEach {
                    it.locations = locations[it.id]
                }
                dumpEpisodes(episodeDumpFile, existingEpisodes)
            }
        }
}

private fun dumpEpisodes(
    episodeDumpFile: File,
    existingEpisodes: MutableMap<Int, Episode>
) {
    episodeDumpFile.delete()
    existingEpisodes
        .values
        .sortedBy { it.id }
        .reversed()
        .forEach {
            val episodeJson = Json.encodeToString(it)
            episodeDumpFile.appendText(episodeJson + "\n")
        }
}

fun String.checksum(): Long {
    val bytes = this.toByteArray()
    val crc32 = CRC32()
    crc32.update(bytes, 0, bytes.size)
    return crc32.value
}

fun parseXmlFile(file: File): Document {
    return try {
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
    } catch (e: Exception) {
        throw Exception("Unable to parse RSS feed XML", e)
    }
}

fun getEpisodeElements(document: Document): Map<Int, Element> {
    val rssElement = document.documentElement
    if (rssElement.tagName != "rss") {
        throw IllegalArgumentException("Invalid RSS feed XML, missing 'rss' start tag.")
    }
    val channelElement = rssElement.getElementsByTagName("channel").item(0) as Element
    val items = channelElement.getElementsByTagName("item")
    val episodes = mutableMapOf<Int, Element>()
    for (i in 0 until items.length) {
        val itemElement = items.item(i) as Element
        val title = itemElement.getSingleChildText("title")
        if (!title.startsWith("GAG")) continue // skip Feedback (FGAG) or Bonus / Extra episodes.
        val episodeNumber = itemElement.getSingleChildText("itunes:episode").toInt()
        episodes[episodeNumber] = itemElement
    }
    return episodes
}

private fun parseXmlEpisode(
    itemElement: Element,
): Episode {
    val episodeNumber = itemElement.getSingleChildText("itunes:episode").toInt()
    val title = itemElement.getSingleChildText("title")
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
        checksum = itemElement.textContent.checksum(),
        title = normalizeWhitespace(title.replace(Regex("GAG\\d\\d\\d?: "), "")),
        // Wed, 29 May 2024 07:00:00 +0000
        date = ZonedDateTime.parse(pubDate, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toKotlinInstant(),
        websiteUrl = URI("https://gadg.fm/$episodeNumber"),
        audioUrl = URI(audioUrl),
        durationInSeconds = durationInSeconds,
        episodeLinks = episodeLinks,
        description = descriptionNormalized,
        temporalLinks = temporalLinks,
        transcript = null, // TODO scrape from musixmatch
        literature = null, // TODO extract from feed.xml
        locations = null,
    )
    return episode
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
    .map { line -> normalizeWhitespace(line) }
    .joinToString(" ")

private fun normalizeWhitespace(line: String) = line
    .filter { it == ' ' || !it.isWhitespace() }
    .replace("  ", " ")
    .trim()

fun extractTemporalRefs(descriptionNormalized: String): List<TemporalRef> {
    val links = mutableListOf<TemporalRef>()

    // Examples:
    // 14. September 2022
    // September 2022
    // 19. Jahrhunderts?
    // Jahr 2022
    // 2000er Jahren?
    // 30er Jahren? des 20. (JH|Jahrhunderts?)
    // TODO des Jahres 1970
    val monthNames = "Januar|Februar|März|April|Mai|Juni|Juli|August|September|Oktober|November|Dezember"
    val regexToModes = listOf(
        Regex("\\d\\d?\\. ($monthNames) \\d{1,4}( vdZw)?") to TemporalRef.Companion.Mode.Day,
        Regex("($monthNames) \\d{1,4}( vdZw)?") to TemporalRef.Companion.Mode.Month,
        Regex("\\d{1,4}er Jahren? des \\d\\d?\\. (JH|Jahrhunderts?)( vdZw)?") to TemporalRef.Companion.Mode.DecadeRelative,
        Regex("\\d{1,4}er Jahren?( vdZw)?") to TemporalRef.Companion.Mode.DecadeAbsolute,
        Regex("\\d\\d?\\. Jahrhunderts?( vdZw)?") to TemporalRef.Companion.Mode.Century,
        Regex("Jahr \\d{1,4}( vdZw)?") to TemporalRef.Companion.Mode.Year,
        // only 2 cases so far (episodes 118 and 158), both of them contain other date refs in their description.
        // Regex("\\d{1,2}er Jahren?( vdZw)?") to TemporalRef.Companion.Mode.JzImplicit,
    )
    var processedIdx = 0
    @Suppress("ControlFlowWithEmptyBody")
    while (regexToModes.any { regexToMode ->
            regexToMode.first.find(descriptionNormalized, processedIdx)?.let { match ->
                processedIdx = match.range.endInclusive
                links.add(
                    TemporalRef(
                        literal = match.value,
                        mode = regexToMode.second,
                        vdzw = match.value.endsWith("vdZw", ignoreCase = true)
                    )
                )
                true
            } == true
        });

    return links.toSet().sortedBy { it.mode }.toList()
}

@Serializable
data class TemporalRef(
    val literal: String,
    val mode: Mode,
    val normalized: String,
    val start: Instant,
    val end: Instant
) {

    // TODO make TemporalRef parsing more nice
    constructor(literal: String, mode: Mode, vdzw: Boolean) : this(
        literal = literal,
        mode = mode,
        normalized = literal
            .replace("Jahr ", "")
            .replace("er Jahren", "er Jahre")
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

        private fun LocalDate.startOfDayUTCSeconds(): java.time.Instant =
            atStartOfDay().toInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS)

        private fun LocalDate.endOfDayUTCSeconds(): java.time.Instant =
            atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS)

        fun getStart(literal: String, mode: Mode, bce: Boolean): Instant =
            when (mode) {
                Mode.Day -> {
                    parseGermanDate(literal).atStartOfDay().toInstant(ZoneOffset.UTC)
                        .truncatedTo(ChronoUnit.SECONDS)
                }

                Mode.Month -> {
                    parseGermanDate("1. $literal").atStartOfDay().toInstant(ZoneOffset.UTC)
                        .truncatedTo(ChronoUnit.SECONDS)
                }

                Mode.Century -> {
                    val century = literal.substringBefore(". Jahrhundert").toInt() * 100
                    if (bce) {
                        parseGermanDate("1. Januar -$century")
                    } else {
                        parseGermanDate("1. Januar ${century - 99}")
                    }.startOfDayUTCSeconds()
                }

                Mode.Year -> {
                    val year = literal.lowercase().substringBefore(" vdzw").substringAfter("jahr ").toInt()
                    if (bce) {
                        parseGermanDate("1. Januar ${-year}")
                    } else {
                        parseGermanDate("1. Januar $year")
                    }.startOfDayUTCSeconds()
                }

                Mode.DecadeAbsolute -> {
                    val decade = literal.substringBefore("er Jahre").toInt()
                    if (bce) {
                        parseGermanDate("1. Januar ${-decade - 9}")
                    } else {
                        val fixedDecade = if (decade == 0) 1 else decade
                        parseGermanDate("1. Januar $fixedDecade")
                    }.startOfDayUTCSeconds()
                }

                Mode.DecadeRelative -> {
                    val century = literal.substringBefore(". Jahrhundert", missingDelimiterValue = "")
                        .ifEmpty { literal.substringBefore(". JH") }.substringAfter(" des ").toInt() * 100
                    val decade = literal.substringBefore("er Jahre").toInt()
                    if (bce) {
                        parseGermanDate("1. Januar ${-(decade + 9 + century - 100)}")
                    } else {
                        parseGermanDate("1. Januar ${decade + century - 100}")
                    }.startOfDayUTCSeconds()
                }
            }.toKotlinInstant()

        fun getEnd(literal: String, mode: Mode, bce: Boolean): Instant =
            when (mode) {
                Mode.Day -> {
                    parseGermanDate(literal).atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC)
                        .truncatedTo(ChronoUnit.SECONDS)
                }

                Mode.Month -> {
                    parseGermanDate("1. $literal").atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC)
                        .truncatedTo(ChronoUnit.SECONDS)
                }

                Mode.Century -> {
                    val century = literal.substringBefore(". Jahrhundert").toInt() * 100
                    if (bce) {
                        parseGermanDate("31. Dezember ${-century + 99}")
                    } else {
                        parseGermanDate("31. Dezember $century")
                    }.endOfDayUTCSeconds()
                }

                Mode.Year -> {
                    val year = literal.lowercase().substringBefore(" vdzw").substringAfter("jahr ").toInt()
                    if (bce) {
                        parseGermanDate("31. Dezember ${-year}")
                    } else {
                        parseGermanDate("31. Dezember $year")
                    }.endOfDayUTCSeconds()
                }

                Mode.DecadeAbsolute -> {
                    val decade = literal.substringBefore("er Jahre").toInt()
                    if (bce) {
                        val fixedDecade = if (decade == 0) 1 else decade
                        parseGermanDate("31. Dezember ${-fixedDecade}")
                    } else {
                        parseGermanDate("31. Dezember ${decade + 9}")
                    }.endOfDayUTCSeconds()
                }

                Mode.DecadeRelative -> {
                    val century = literal.substringBefore(". Jahrhundert", missingDelimiterValue = "")
                        .ifEmpty { literal.substringBefore(". JH") }.substringAfter(" des ").toInt() * 100
                    val decade = literal.substringBefore("er Jahre").toInt()
                    if (bce) {
                        val fixedDecade = if (decade == 0) 1 else decade
                        parseGermanDate("31. Dezember ${-(century - 100 + fixedDecade)}")
                    } else {
                        parseGermanDate("31. Dezember ${century - 100 + decade + 9}")
                    }.endOfDayUTCSeconds()
                }
            }.toKotlinInstant()


        enum class Mode {
            Day, Month, Year, DecadeAbsolute, DecadeRelative, Century
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
    val checksum: Long = 0L,
    val title: String,
    val date: Instant,
    val durationInSeconds: Long,
    @Serializable(with = URISerializer::class)
    val websiteUrl: URI,
    @Serializable(with = URISerializer::class)
    val audioUrl: URI,
    val transcript: String? = null,
    val description: String,
    val episodeLinks: List<Int>,
    val temporalLinks: List<TemporalRef>,
    var locations: List<Location>? = null,
    val literature: List<String>? = null,
)

@Serializable
data class Location(val name: String, val latitude: Double, val longitude: Double)

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
