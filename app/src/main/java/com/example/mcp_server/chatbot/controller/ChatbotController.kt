package com.example.mcp_server.chatbot.controller

import com.example.mcp_server.chatbot.ai.OpenAIAssistant
import com.example.mcp_server.chatbot.protocol.MCPResponse
import com.example.mcp_server.chatbot.protocol.MCPServerApi
import com.example.mcp_server.chatbot.protocol.MCPTool
import com.example.mcp_server.chatbot.tools.BraveSearchMCPTool
import com.example.mcp_server.chatbot.tools.GithubMCPTools
import com.example.mcp_server.chatbot.tools.PuppeteerMCPTool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

// ChatbotController coordinates between user, OpenAI assistant and MCP tools
class ChatbotController(
    private val openAIAssistant: OpenAIAssistant,
    private val mcpToolRouter: MCPServerApi.MCPToolRouter
) {
    // Process user message and return response
    suspend fun processUserMessage(userMessage: String): Flow<String> = flow {
        // First check if a tool can handle this query
        val tool = mcpToolRouter.findToolForQuery(userMessage)

        if (tool != null) {
            // Execute the tool and get response
            val toolResponse = executeToolAndGetResponse(tool, userMessage)

            // Add tool response to OpenAI assistant context
            openAIAssistant.addToolResponse(toolResponse.response)

            // Get final response from OpenAI that incorporates the tool response
            val finalResponse = openAIAssistant.getChatCompletion(
                userMessage = "Please respond to the user based on this tool result: ${toolResponse.response}"
            )

            // Emit the final response
            finalResponse.collect { emit(it) }
        } else {
            // No tool can handle this, so use OpenAI directly
            val assistantResponse = openAIAssistant.getChatCompletion(userMessage)
            assistantResponse.collect { emit(it) }
        }
    }

    // Execute tool and get response
    private suspend fun executeToolAndGetResponse(tool: MCPTool, query: String): MCPResponse {
        return try {
            // Execute tool
            val response = mcpToolRouter.executeQuery(query)

            // If there's an error, create a default response
            if (response.error != null) {
                MCPResponse(
                    response = "I tried to ${tool.id} for you, but encountered an error: ${response.error}",
                    error = response.error
                )
            } else {
                response
            }
        } catch (e: Exception) {
            // Handle exceptions
            MCPResponse(
                response = "I tried to ${tool.id} for you, but encountered an error: ${e.message}",
                error = e.stackTraceToString()
            )
        }
    }

    // Clear conversation history
    fun clearConversation() {
        openAIAssistant.clearConversation()
    }
}

// MCPToolManager creates and manages MCP tools
class MCPToolManager(config: MCPToolConfig) {
    // MCP Tool router
    val toolRouter: MCPServerApi.MCPToolRouter

    init {
        // Create tools based on configuration
        val tools = mutableListOf<MCPTool>()

        // Add GitHub tool if configured
        if (config.githubConfig != null) {
            tools.add(GithubMCPTools(
                baseUrl = config.githubConfig.baseUrl,
                apiKey = config.githubConfig.apiKey
            ))
        }

        // Add Brave Search tool if configured
        if (config.braveSearchConfig != null) {
            tools.add(
                BraveSearchMCPTool(
                baseUrl = config.braveSearchConfig.baseUrl,
                apiKey = config.braveSearchConfig.apiKey
            )
            )
        }

        // Add Puppeteer tool if configured
        if (config.puppeteerConfig != null) {
            tools.add(
                PuppeteerMCPTool(
                baseUrl = config.puppeteerConfig.baseUrl
            )
            )
        }

        // Initialize tool router with tools
        toolRouter = MCPServerApi.MCPToolRouter(tools)
    }
}

// Configuration for MCP tools
data class MCPToolConfig(
    val githubConfig: GitHubConfig? = null,
    val braveSearchConfig: BraveSearchConfig? = null,
    val puppeteerConfig: PuppeteerConfig? = null
)

data class GitHubConfig(
    val baseUrl: String,
    val apiKey: String
)

data class BraveSearchConfig(
    val baseUrl: String,
    val apiKey: String
)

data class PuppeteerConfig(
    val baseUrl: String
)