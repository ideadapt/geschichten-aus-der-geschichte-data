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
import org.slf4j.LoggerFactory

/**
 * OpenAI client featuring methods to analyze gag descriptions:
 *  - ...
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
    suspend fun extractGeoLocations(texts: List<String>): List<String> {
        logger.info("extracting geo locations")
        val assistantName = "asst_gag-map_geo-locations_v1"
        val assistant = ai.assistants().find { it.name == assistantName } ?: ai.assistant(
            AssistantRequest(
                name = assistantName,
                model = ModelId("gpt-4o-mini"),
                instructions = """
        |You can extract location data (coordinates and names) from description texts.
        |You know coordinates of places, buildings, villages, cities, street names, regions and countries.
        |
        |You follow these procedure:
        | For each input line: 
        |  First, extract location names and their coordinates. 
        |  Then, output these two findings in the following format "<location-name>;<coordinates>\n".
        |  The format for coordinates is decimal degrees (DD), e.g. 41.40338, 2.17403
        |  Proceed with the next line.
        |
        |Don't do any further output.
        |""".trimMargin()
            )
        )

        val aiThreadRun = ai.createThreadRun(
            request = ThreadRunRequest(
                assistantId = assistant.id,
                thread = threadRequest {
                    messages = listOf(
                        ThreadMessage(
                            content = texts.joinToString("\n"),
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
            if (text == null) logger.error("expected MessageContent.Text. Ignoring message ${it.id}.")
            text
        }
            .map { it.text.value }
            .first() // 1: locations, 2: the prompt

        val geoLocationsLines = output.lines()

        ai.delete(aiThreadRun.threadId)

        return geoLocationsLines
    }
}
