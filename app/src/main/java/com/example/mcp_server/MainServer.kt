package com.example.mcp_server

import android.content.Context
import com.example.mcp_server.chatbot.ai.OpenAIAssistant
import com.example.mcp_server.chatbot.controller.BraveSearchConfig
import com.example.mcp_server.chatbot.controller.ChatbotController
import com.example.mcp_server.chatbot.controller.GitHubConfig
import com.example.mcp_server.chatbot.controller.MCPToolConfig
import com.example.mcp_server.chatbot.controller.MCPToolManager
import com.example.mcp_server.chatbot.controller.PuppeteerConfig
import io.github.cdimascio.dotenv.Dotenv
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import org.slf4j.LoggerFactory

// Main application class
fun main(context: Context? = null) {
    val logger = LoggerFactory.getLogger("MainServer")
    logger.info("Starting MCP Server...")

    try {
        // Load environment variables
        val dotenv = loadEnvironmentVariables(context)

        // Get API keys from environment variables
        val openAiApiKey = dotenv["OPENAI_API_KEY"] ?: throw IllegalStateException("OPENAI_API_KEY not set")
        val githubApiKey = dotenv["GITHUB_API_KEY"] ?: throw IllegalStateException("GITHUB_API_KEY not set")
        val braveSearchApiKey = dotenv["BRAVE_SEARCH_API_KEY"] ?: throw IllegalStateException("BRAVE_SEARCH_API_KEY not set")

        logger.info("Environment variables loaded successfully")

        // MCP tool configuration
        val mcpToolConfig = MCPToolConfig(
            githubConfig = GitHubConfig(
                baseUrl = dotenv["GITHUB_MCP_URL"] ?: "http://localhost:3000/",
                apiKey = githubApiKey
            ),
            braveSearchConfig = BraveSearchConfig(
                baseUrl = dotenv["BRAVE_SEARCH_MCP_URL"] ?: "http://localhost:3001/",
                apiKey = braveSearchApiKey
            ),
            puppeteerConfig = PuppeteerConfig(
                baseUrl = dotenv["PUPPETEER_MCP_URL"] ?: "http://localhost:3002/"
            )
        )

        // Initialize tool manager and router
        val toolManager = MCPToolManager(mcpToolConfig)
        logger.info("MCP Tool Manager initialized")

        // Initialize OpenAI assistant
        val openAIAssistant = OpenAIAssistant(openAiApiKey)
        logger.info("OpenAI Assistant initialized")

        // Initialize chatbot controller
        val chatbotController = ChatbotController(
            openAIAssistant = openAIAssistant,
            mcpToolRouter = toolManager.toolRouter
        )
        logger.info("Chatbot Controller initialized")

        // Start web server
        startWebServer(chatbotController)
        logger.info("Web server started")
    } catch (e: Exception) {
        logger.error("Error starting server: ${e.message}", e)
        throw e
    }
}

// Load environment variables from .env file or Android assets
private fun loadEnvironmentVariables(context: Context?): Dotenv {
    return try {
        if (context != null) {
            // Android environment: Try to load from assets
            val configDir = File(context.filesDir, "config")
            if (!configDir.exists()) {
                configDir.mkdirs()
            }

            // Check if .env exists in the app's files directory
            val envFile = File(configDir, ".env")
            if (!envFile.exists()) {
                // If .env doesn't exist, create it from assets
                context.assets.open(".env").use { input ->
                    envFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            Dotenv.configure()
                .directory(configDir.absolutePath)
                .ignoreIfMissing()
                .load()
        } else {
            // Non-Android environment (for testing)
            val workingDir = System.getProperty("user.dir")
            Dotenv.configure()
                .directory("$workingDir/config")
                .ignoreIfMissing()
                .load()
        }
    } catch (e: Exception) {
        LoggerFactory.getLogger("MainServer").error("Error loading environment variables: ${e.message}", e)
        // Fallback to empty Dotenv
        Dotenv.configure().ignoreIfMissing().load()
    }
}

// Start Ktor web server
fun startWebServer(chatbotController: ChatbotController) {
    val logger = LoggerFactory.getLogger("WebServer")

    try {
        // Use a different port on Android to avoid conflicts
        val serverPort = 8080
        logger.info("Starting server on port $serverPort")

        // Create and start server
        val server = embeddedServer(Netty, port = serverPort) {
            // Configure server
            configureServer(this, chatbotController)
        }

        server.start(wait = true)
    } catch (e: Exception) {
        logger.error("Failed to start web server: ${e.message}", e)
        throw e
    }
}

// Configure Ktor server
fun configureServer(application: Application, chatbotController: ChatbotController) {
    with(application) {
        // Install features
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }

        install(CORS) {
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Delete)
            allowMethod(HttpMethod.Patch)
            allowHeader(HttpHeaders.AccessControlAllowHeaders)
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.AccessControlAllowOrigin)
            anyHost()
        }

        // Configure routing
        routing {
            // Static files for web UI
            static("/") {
                resources("static")
                defaultResource("static/index.html")
            }

            // API endpoints
            route("/api") {
                // Chat endpoint
                post("/chat") {
                    try {
                        val chatRequest = call.receive<ChatRequest>()
                        LoggerFactory.getLogger("API").info("Received chat request: ${chatRequest.message}")

                        // Process message through controller and collect the response
                        var responseText = ""
                        chatbotController.processUserMessage(chatRequest.message).collect {
                            responseText = it
                        }

                        // Send response back
                        call.respond(ChatResponse(response = responseText))
                    } catch (e: Exception) {
                        LoggerFactory.getLogger("API").error("Error processing chat request: ${e.message}", e)
                        call.respond(
                            status = HttpStatusCode.InternalServerError,
                            message = ChatResponse(response = "Sorry, an error occurred: ${e.message}")
                        )
                    }
                }

                // Clear conversation endpoint
                post("/clear") {
                    try {
                        chatbotController.clearConversation()
                        call.respond(mapOf("status" to "success"))
                    } catch (e: Exception) {
                        LoggerFactory.getLogger("API").error("Error clearing conversation: ${e.message}", e)
                        call.respond(
                            status = HttpStatusCode.InternalServerError,
                            message = mapOf("status" to "error", "message" to e.message)
                        )
                    }
                }

                // Health check endpoint
                get("/health") {
                    call.respond(mapOf("status" to "UP"))
                }
            }
        }
    }
}

// Data classes for API requests/responses
@Serializable
data class ChatRequest(
    val message: String
)

@Serializable
data class ChatResponse(
    val response: String
)