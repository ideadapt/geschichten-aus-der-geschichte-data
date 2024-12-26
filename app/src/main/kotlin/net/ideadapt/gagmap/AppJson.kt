package net.ideadapt.gagmap

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import kotlinx.serialization.json.JsonNames
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers


@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
@Serializable
data class PodigeeDto(
    val episode: EpisodeDto
)

@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
@Serializable
data class EpisodeDto(
    val media: MediaDto,
    val number: Int,
    val title: String,
    val subtitle: String,
    @JsonNames("duration")
    val durationInSeconds: Int,
    @JsonNames("published_at")
    val publishedAt: kotlinx.datetime.Instant,
    val description: String,
)

@Serializable
data class MediaDto(
    @Serializable(with = URISerializer::class)
    val mp3: URI
)

fun main() {
    val db = File("data/episodes.jsonl")
    val maxStoredNumber = db.readLines().maxOfOrNull { Json.decodeFromString<EpisodeDto>(it).number }

    val minEpisodeNumber = (maxStoredNumber ?: 0) + 1
    val maxEpisodeNumber = 3 //483
    (minEpisodeNumber..maxEpisodeNumber).map { episodeNumber ->
        val url = "https://geschichten-aus-der-geschichte.podigee.io/$episodeNumber-gag${
            String.format(
                "%02d",
                episodeNumber
            )
        }-abc/embed?context=external"

        val resp = HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI(url))
                    .headers("accept", "application/json")
                    .GET()
                    .build(), BodyHandlers.ofString()
            )
            .body()

        val episodeDto = Json.decodeFromString<PodigeeDto>(resp).episode
        val episodeJson = Json.encodeToString(episodeDto)
        db.appendText(episodeJson + "\n")
    }
}
