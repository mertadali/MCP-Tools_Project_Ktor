package com.example.mcp_server



// Main application class
fun main() {
    // Load environment variables
    val dotenv = dotenv {
        // Android'de tam yol kullanın
        directory = System.getProperty("user.dir") + "/config"
        ignoreIfMissing = true
    }
    // Get API keys from environment variables
    val openAiApiKey = dotenv["OPENAI_API_KEY"] ?: throw IllegalStateException("OPENAI_API_KEY not set")
    val githubApiKey = dotenv["GITHUB_API_KEY"] ?: throw IllegalStateException("GITHUB_API_KEY not set")
    val braveSearchApiKey = dotenv["BRAVE_SEARCH_API_KEY"] ?: throw IllegalStateException("BRAVE_SEARCH_API_KEY not set")

    // MCP tool configuration
    val mcpToolConfig = MCPToolConfig(
        githubConfig = GitHubConfig(
            baseUrl = "http://localhost:3000/", // Change to actual MCP GitHub server URL
            apiKey = githubApiKey
        ),
        braveSearchConfig = BraveSearchConfig(
            baseUrl = "http://localhost:3001/", // Change to actual MCP Brave Search server URL
            apiKey = braveSearchApiKey
        ),
        puppeteerConfig = PuppeteerConfig(
            baseUrl = "http://localhost:3002/" // Change to actual MCP Puppeteer server URL
        )
    )

    // Initialize tool manager and router
    val toolManager = MCPToolManager(mcpToolConfig)

    // Initialize OpenAI assistant
    val openAIAssistant = OpenAIAssistant(openAiApiKey)

    // Initialize chatbot controller
    val chatbotController = ChatbotController(
        openAIAssistant = openAIAssistant,
        mcpToolRouter = toolManager.toolRouter
    )

    // Start web server
    startWebServer(chatbotController)
}

// Start Ktor web server
fun startWebServer(chatbotController: ChatbotController) {
    // Create server
    val server = embeddedServer(Netty, port = 8080) {
        // Install features
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
            })
        }
        install(CORS) {
            method(HttpMethod.Options)
            method(HttpMethod.Get)
            method(HttpMethod.Post)
            method(HttpMethod.Put)
            method(HttpMethod.Delete)
            method(HttpMethod.Patch)
            header(HttpHeaders.AccessControlAllowHeaders)
            header(HttpHeaders.ContentType)
            header(HttpHeaders.AccessControlAllowOrigin)
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
                    val chatRequest = call.receive<ChatRequest>()

                    // Process message through controller
                    val responseFlow = chatbotController.processUserMessage(chatRequest.message)
                        .flowOn(Dispatchers.IO)

                    // Collect response
                    val response = withContext(Dispatchers.IO) {
                        responseFlow.collect { response ->
                            call.respond(ChatResponse(response = response))
                        }
                    }
                }

                // Clear conversation endpoint
                post("/clear") {
                    chatbotController.clearConversation()
                    call.respond(mapOf("status" to "success"))
                }

                // Health check endpoint
                get("/health") {
                    call.respond(mapOf("status" to "UP"))
                }
            }
        }
    }

    // Start server
    server.start(wait = true)
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