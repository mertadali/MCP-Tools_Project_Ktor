package com.example.mcp_server.chatbot.protocol

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.Call


// MCP Request model
data class MCPRequest(
    val context : MCPContext,
    val query  : String
)

// Context information for MCP
data class MCPContext(
    val toolId : String,
    val parameters  : Map<String,String> = mapOf(),
    val metadata: Map<String,String> = mapOf()
)

// MCP Response model
data class MCPResponse(
    val response: String,
    val context: MCPContext? = null,
    val error: String? = null
)

// Tool interface that all MCP tools must implement
interface MCPTool {
    val id : String
    val actionNames : List<String>

    suspend fun execute(request : MCPRequest) : MCPResponse

    fun canHandle(query: String) : Boolean
}

// Tool interface that all MCP tools must implement


interface MCPServerApi{
    @POST("execute")
    fun execute(@Body request: MCPRequest) : Call<MCPResponse>

    // MCP Tool types

    enum class MCPToolType {
        @SerializedName("github")
        GITHUB,

        @SerializedName("brave-search")
        BRAVE_SEARCH,

        @SerializedName("puppeteer")
        PUPPETEER,

        @SerializedName("custom")
        CUSTOM
    }


    // Abstract base class for MCP tools
    abstract class BaseMCPTool(
        override val id: String,
        val type: MCPToolType
    ) : MCPTool {

        // Common patterns for recognizing tool triggers in natural language
        protected val triggerPatterns = mutableListOf<Regex>()

        override fun canHandle(query: String): Boolean {
            return triggerPatterns.any { it.containsMatchIn(query.lowercase()) }
        }
    }

    // MCP Tool Router to direct queries to appropriate tools
    class MCPToolRouter(private val tools: List<MCPTool>) {

        // Find the appropriate tool for the query
        fun findToolForQuery(query: String): MCPTool? {
            return tools.firstOrNull { it.canHandle(query) }
        }

        // Execute a query using the appropriate tool
        suspend fun executeQuery(query: String): MCPResponse {
            val tool = findToolForQuery(query) ?: return MCPResponse(
                response = "I don't have a tool to handle this request.",
                error = "No matching tool found"
            )

            val request = MCPRequest(
                context = MCPContext(toolId = tool.id),
                query = query
            )

            return tool.execute(request)
        }
    }


}
