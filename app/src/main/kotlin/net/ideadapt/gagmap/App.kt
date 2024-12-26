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
        val itemTitle = itemElement.getSingleChildText("title")

        if (!itemTitle.startsWith("GAG")) continue;

        val episodeNumber = itemElement.getSingleChildText("itunes:episode").toInt()
        val formattedEpisodeNumber = String.format("%02d", episodeNumber)
        val pubDate = itemElement.getSingleChildText("pubDate")
        val contentEncoded = itemElement.getSingleChildText("content:encoded")
        val durationInSeconds = itemElement.getSingleChildText("itunes:duration").toLong()
        val audioUrl = itemElement.getSingleChild("enclosure").attributes.getNamedItem("url").textContent

        // gadg.fm/362
        // geschichte.fm/podcast/zs104
        val toSearch = contentEncoded
        val links = toSearch.takeIf { it.isNotEmpty() }?.let {
            Regex("(gadg\\.fm/|geschichte\\.fm/podcast/zs)(\\d\\d\\d?)").findAll(it)
                .map { m ->
                    m.groupValues[2].toInt()
                }
        }
            .orEmpty()
            .filter { it != episodeNumber }
            .toList()

        val episode = Episode(
            id = episodeNumber,
            title = itemTitle,
            // Wed, 29 May 2024 07:00:00 +0000
            date = ZonedDateTime.parse(pubDate, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toKotlinInstant(),
            websiteUrl = URI("https://www.geschichte.fm/archiv/zs$formattedEpisodeNumber"),
            audioUrl = URI(audioUrl),
            durationInSeconds = durationInSeconds,
            episodeLinks = links,
            transcript = "",
            description = "...", // itemContentEncoded,
        )
        episodes.add(episode)
    }
    return episodes
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
    val episodeLinks: List<Int>
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
