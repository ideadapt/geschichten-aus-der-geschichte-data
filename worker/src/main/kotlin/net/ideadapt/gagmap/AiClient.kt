package net.ideadapt.gagmap

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.core.LogLevel
import com.openai.models.responses.ResponseCreateParams
import com.openai.models.responses.Tool
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.stream.Collectors

/**
 * OpenAI client featuring methods to analyze gag episode descriptions:
 *  - location names with coordinates
 */
class AiClient(
    private val token: String = requireNotNull(Config.get("OPEN_AI_TOKEN")) { "OPEN_AI_TOKEN missing" },
    private val ai: OpenAIClient = OpenAIOkHttpClient.builder().apiKey(token).logLevel(LogLevel.INFO).build()
) {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    fun extractGeoLocations(texts: Map<Int, String>): Map<Int, List<Location>?> {
        logger.debug("Extracting geo locations")

        val params: ResponseCreateParams = ResponseCreateParams.builder().instructions(
            """
        |# Context
        |You can extract location data (coordinates and names) from description texts.
        |You know coordinates of places, buildings, villages, cities, street names, regions and countries.
        |
        |# Procedure
        |You follow these exact procedure for each input line: 
        |  0. The line starts with a numeric ID, followed by a ",". Remember that ID.
        |  1. Find location names (such as places, buildings, villages, cities, street names, regions, states or countries) in the input line text. If you can't find any on the first try, try another time. Most often, there are some geographic information / location names.
        |  2. For each location name: 
        |   2.1 Determine its coordinates (Hint: the coordinates are not contained in the input line itself.). If you can't figure out coordinates, just use the value 0.
        |  3. Create a JSON array, containing one object per location. The JSON schema is:
        |  
        |   [{"name": string, "latitude": number, "longitude": number}]
        |   
        |  Hint: The format for the coordinates (latitude and longitude) is decimal degrees (DD), e.g. 41.40338, 2.17403.
        |  
        |  4. Output the ID from step 0, followed by a colon ":" and then the JSON array (all on one line). DO NOT ADD ANY PRE- POST-TEXT, just the plain extracted data.
        |  5. Proceed with the next line.
        |  
        |# Example
        |The following two input lines:
        |
        |123,Today we talk about the Niagara Falls.
        |2,In 1993 I crossed Switzerland by foot and then drove to Berlin by car.
        |
        |Would output:
        |
        |123:[{"name": "Niagara Falls", "latitude": 43.08218804473803, "longitude": -79.07252065386227}]
        |2:[{"name": "Switzerland", "latitude": 46.91269861851872, "longitude": 8.240502621260882},{"name": "Berlin", "latitude": 52.51812698340202, "longitude": 13.415805358960382}]
        |""".trimMargin()
            )
            .input(texts.map { it.key.toString() + "," + it.value }.joinToString("\n"))
            .addCodeInterpreterTool(Tool.CodeInterpreter.Container.CodeInterpreterToolAuto.builder().build())
            .model("gpt-4o-mini")
            .build()

        val geoLocationLines = ai.responses().create(params).output().stream()
            .flatMap { item -> item.message().stream() }
            .flatMap { message -> message.content().stream() }
            .flatMap { content -> content.outputText().stream() }
            .map { text -> text.text() }
            .collect(Collectors.toList()).first().lines()

        return geoLocationLines
            .associate {
                val episodeId = try {
                    it.substringBefore(":").toInt()
                } catch (ex: Exception) {
                    logger.info("No episodeId prefix in AI response '$it'. Ignoring response.")
                    logger.error(ex.message, ex)
                    -1
                }
                try {
                    val locations = Json.decodeFromString<List<Location>>(it.substringAfter(":"))
                    episodeId to locations
                } catch (ex: Exception) {
                    logger.error(ex.message, ex)
                    episodeId to null
                }
            }
            .mapValues { entry ->
                entry.value?.filter { it.latitude != 0.0 && it.longitude != 0.0 }
            }
            .withDefault { null }
    }
}
