package net.ideadapt.gagmap

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
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
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

fun extractTemporalRefs(descriptionNormalized: String): List<String> {
    // 14. September 2022
    // September 2022 (vdZw)?
    // 19. Jahrhundert (vdZw)?
    // Jahr 2022 (vdZw)?
    // 2000er Jahre (vdZw)?
    val monthNames = "Januar|Februar|März|April|Mai|Juni|Juli|August|September|Oktober|November|Dezember"
    val temporalLinks =
        listOf(
            Regex("(\\d\\d?\\. )?($monthNames) \\d{1,4}( vdZw)?"),
            Regex("\\d\\d?\\. Jahrhundert( vdZw)?"),
            Regex("Jahr \\d{1,4}( vdZw)?"),
            Regex("\\d{1,4}er Jahre( vdZw)?"),
        ).flatMap { regex ->
            regex
                .findAll(descriptionNormalized)
                .map { it.value }
        }

    return temporalLinks
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
    val date: kotlinx.datetime.Instant,
    val durationInSeconds: Long,
    @Serializable(with = URISerializer::class)
    val websiteUrl: URI,
    @Serializable(with = URISerializer::class)
    val audioUrl: URI,
    val transcript: String,
    val description: String,
    val episodeLinks: List<Int>,
    val temporalLinks: List<String>,
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
