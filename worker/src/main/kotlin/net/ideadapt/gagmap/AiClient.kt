package net.ideadapt.gagmap

import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.assistant.AssistantRequest
import com.aallam.openai.api.core.Role
import com.aallam.openai.api.core.Status
import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.api.logging.Logger
import com.aallam.openai.api.message.MessageContent
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.api.run.ThreadRunRequest
import com.aallam.openai.api.thread.ThreadMessage
import com.aallam.openai.api.thread.threadRequest
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * OpenAI client featuring methods to analyze gag episode descriptions:
 *  - location names with coordinates
 */
class AiClient(
    private val token: String = requireNotNull(Config.get("OPEN_AI_TOKEN")) { "OPEN_AI_TOKEN missing" },
    private val ai: OpenAI = OpenAI(
        config = OpenAIConfig(
            token = token,
            logging = LoggingConfig(LogLevel.Info, Logger.Default),
        )
    )
) {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    @OptIn(BetaOpenAI::class)
    suspend fun extractGeoLocations(texts: Map<Int, String>): Map<Int, List<Location>?> {
        logger.debug("Extracting geo locations")
        val assistantName = "asst_gag-map_geo-locations_v7"
        val assistant = ai.assistants().find { it.name == assistantName } ?: ai.assistant(
            AssistantRequest(
                name = assistantName,
                model = ModelId("gpt-4o-mini"),
                instructions = """
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
        |  4. Output the ID from step 0, followed by a colon ":" and then the JSON array (all on one line).
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
        )

        val aiThreadRun = ai.createThreadRun(
            request = ThreadRunRequest(
                assistantId = assistant.id,
                thread = threadRequest {
                    messages = listOf(
                        ThreadMessage(
                            content = texts.map { it.key.toString() + "," + it.value }.joinToString("\n"),
                            role = Role.User
                        )
                    )
                }
            ))

        do {
            delay(1500)
            val retrievedRun = ai.getRun(threadId = aiThreadRun.threadId, runId = aiThreadRun.id)
        } while (retrievedRun.status != Status.Completed)

        val output = ai.messages(aiThreadRun.threadId).mapNotNull {
            val text = it.content.first() as? MessageContent.Text
            if (text == null) logger.error("Expected MessageContent.Text. Ignoring message ${it.id}.")
            text
        }
            .map { it.text.value }
            .first() // 1: locations, 2: the prompt

        val geoLocationsLines = output.lines()

        ai.delete(aiThreadRun.threadId)

        return geoLocationsLines
            .associate {
                val episodeId = it.substringBefore(":").toInt()
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
    }
}
