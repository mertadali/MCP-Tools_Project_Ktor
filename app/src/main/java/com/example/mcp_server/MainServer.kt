package com.example.mcp_server

import com.example.mcp_server.chatbot.ai.OpenAIAssistant
import com.example.mcp_server.chatbot.controller.ChatbotController
import com.example.mcp_server.chatbot.protocol.MCPServerApi
import com.example.mcp_server.chatbot.tools.BraveSearchMCPTool
import com.example.mcp_server.chatbot.tools.GithubMCPTools
import com.example.mcp_server.chatbot.tools.PuppeteerMCPTool
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.resources
import io.ktor.server.http.content.static
import io.ktor.server.netty.Netty
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing


// Bu bir standart Kotlin sınıfı, Android Activity değil
class MainServer {
    // Web sunucusunu başlat
    fun startServer(
        openAiApiKey: String,
        githubApiKey: String,
        braveSearchApiKey: String
    ) {
        // MCP araçlarını oluştur
        val githubTool = GithubMCPTools(
            baseUrl = "http://localhost:3000/",
            apiKey = githubApiKey
        )

        val braveSearchTool = BraveSearchMCPTool(
            baseUrl = "http://localhost:3001/",
            apiKey = braveSearchApiKey
        )

        val puppeteerTool = PuppeteerMCPTool(
            baseUrl = "http://localhost:3002/"
        )

        // MCP Tool Router'ı oluştur
        val mcpToolRouter =
            MCPServerApi.MCPToolRouter(listOf(githubTool, braveSearchTool, puppeteerTool))

        // OpenAI asistanını başlat
        val openAIAssistant = OpenAIAssistant(openAiApiKey)

        // Chatbot controller'ı oluştur
        val chatbotController = ChatbotController(openAIAssistant, mcpToolRouter)

        // Ktor web sunucusunu başlat
        val server = embeddedServer(Netty, port = 8080) {
            // Yapılandırma ve rotalar...
            routing {
                static("/") {
                    resources("static")
                }

                route("/api") {
                    post("/chat") {
                        // Chat endpoint'i...
                    }
                }
            }
        }

        // Sunucuyu başlat
        server.start(wait = true)
    }
}