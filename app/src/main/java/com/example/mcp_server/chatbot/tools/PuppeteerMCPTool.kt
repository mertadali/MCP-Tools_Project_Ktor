package com.example.mcp_server.chatbot.tools

import com.example.mcp_server.chatbot.protocol.MCPRequest
import com.example.mcp_server.chatbot.protocol.MCPResponse
import com.example.mcp_server.chatbot.protocol.MCPServerApi
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

// Puppeteer MCP tool implementation for web browsing
class PuppeteerMCPTool(
    baseUrl: String
) : MCPServerApi.BaseMCPTool("puppeteer", MCPServerApi.MCPToolType.PUPPETEER) {

    override val actionNames = listOf(
        "browse",
        "screenshot",
        "extractText",
        "clickButton",
        "fillForm"
    )

    // Initialize trigger patterns for web browsing-related queries
    init {
        triggerPatterns.addAll(listOf(
            Regex("(visit|browse|open|go to)\\s+(website|webpage|site|url|page)\\s+(.+)", RegexOption.IGNORE_CASE),
            Regex("(visit|browse|open|go to)\\s+(.+\\.(com|org|net|io|gov))", RegexOption.IGNORE_CASE),
            Regex("(take|get|capture)\\s+(a\\s+)?(screenshot|image|picture)\\s+of\\s+(.+)", RegexOption.IGNORE_CASE),
            Regex("(extract|get|read|scrape)\\s+(text|content|data)\\s+from\\s+(.+)", RegexOption.IGNORE_CASE)
        ))
    }

    // Create HTTP client with logging
    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(60, TimeUnit.SECONDS) // Longer timeout for web browsing
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // Create Retrofit instance for Puppeteer MCP server
    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    // Create API interface
    private val api = retrofit.create(PuppeteerApi::class.java)

    // Execute Puppeteer tool with given request
    override suspend fun execute(request: MCPRequest): MCPResponse {
        return try {
            val action = determinePuppeteerAction(request.query)
            val url = extractUrl(request.query)

            if (url == null) {
                return MCPResponse(
                    response = "I couldn't determine which website you want me to visit. Please specify a URL.",
                    error = "No URL specified"
                )
            }

            val puppeteerRequest = PuppeteerRequest(
                action = action,
                url = url,
                selector = extractSelector(request.query),
                waitForNavigation = true
            )

            val response = withContext(Dispatchers.IO) {
                api.execute(puppeteerRequest).execute()
            }

            if (response.isSuccessful) {
                val result = response.body()

                if (result != null) {
                    val formattedResponse = formatPuppeteerResponse(result, action, url)
                    MCPResponse(response = formattedResponse)
                } else {
                    MCPResponse(
                        response = "I visited '$url' but couldn't retrieve any content.",
                        error = "Null response body"
                    )
                }
            } else {
                MCPResponse(
                    response = "Error browsing '$url': ${response.message()}",
                    error = response.errorBody()?.string()
                )
            }
        } catch (e: Exception) {
            MCPResponse(
                response = "An error occurred while browsing: ${e.message}",
                error = e.stackTraceToString()
            )
        }
    }

    // Determine which Puppeteer action to perform based on query
    private fun determinePuppeteerAction(query: String): String {
        return when {
            Regex("(screenshot|image|picture)", RegexOption.IGNORE_CASE).containsMatchIn(query) -> "screenshot"
            Regex("(extract|get|read|scrape)\\s+(text|content|data)", RegexOption.IGNORE_CASE).containsMatchIn(query) -> "extractText"
            Regex("(click|press|push)\\s+(button|link)", RegexOption.IGNORE_CASE).containsMatchIn(query) -> "clickButton"
            Regex("(fill|complete|input|enter)\\s+(form|data)", RegexOption.IGNORE_CASE).containsMatchIn(query) -> "fillForm"
            else -> "browse" // Default action
        }
    }

    // Extract URL from query
    private fun extractUrl(query: String): String? {
        // Match URLs with or without protocol
        val urlPattern = Regex("(?:https?://)?(?:www\\.)?([a-zA-Z0-9][-a-zA-Z0-9]*(?:\\.[a-zA-Z0-9][-a-zA-Z0-9]*)+)(?:/[^\\s]*)?", RegexOption.IGNORE_CASE)
        val visitPattern = Regex("(?:visit|browse|open|go to)\\s+(?:the\\s+)?(?:website|webpage|site|url|page)?\\s*(?:at\\s+|for\\s+)?([^\\s]+(?:\\.[a-zA-Z]{2,})[^\\s]*)", RegexOption.IGNORE_CASE)

        // Try to match the URL patterns
        val urlMatch = urlPattern.find(query)?.value
        val visitMatch = visitPattern.find(query)?.groupValues?.get(1)

        // Return the first match found
        return when {
            urlMatch != null -> {
                // Add https:// if protocol is missing
                if (!urlMatch.startsWith("http")) "https://$urlMatch" else urlMatch
            }
            visitMatch != null -> {
                // Add https:// if protocol is missing
                if (!visitMatch.startsWith("http")) "https://$visitMatch" else visitMatch
            }
            else -> null
        }
    }

    // Extract CSS selector from query (if any)
    private fun extractSelector(query: String): String? {
        val selectorPattern = Regex("(?:with|using)\\s+selector\\s+['\"](.+)['\"]", RegexOption.IGNORE_CASE)
        return selectorPattern.find(query)?.groupValues?.get(1)
    }

    // Format Puppeteer response based on action
    private fun formatPuppeteerResponse(result: PuppeteerResponse, action: String, url: String): String {
        return when (action) {
            "screenshot" -> "I captured a screenshot of $url. ${result.message ?: ""}"
            "extractText" -> {
                val contentPreview = result.content?.let {
                    if (it.length > 500) it.substring(0, 500) + "..." else it
                } ?: "No content extracted"

                "Here's the content I extracted from $url:\n\n$contentPreview"
            }
            "clickButton" -> "I clicked the button on $url. ${result.message ?: ""}"
            "fillForm" -> "I filled the form on $url. ${result.message ?: ""}"
            else -> "I browsed $url. ${result.message ?: ""}"
        }
    }
}

// Puppeteer-specific request model
data class PuppeteerRequest(
    val action: String,
    val url: String,
    val selector: String? = null,
    val formData: Map<String, String>? = null,
    val waitTime: Int = 5000,
    val waitForNavigation: Boolean = true,
    val fullPage: Boolean = true
)

// Puppeteer-specific response model
data class PuppeteerResponse(
    val success: Boolean,
    val message: String? = null,
    val content: String? = null,
    val screenshot: String? = null,
    @SerializedName("page_title") val pageTitle: String? = null,
    val error: String? = null
)

// Puppeteer MCP server API interface
interface PuppeteerApi {
    @POST("puppeteer/execute")
    fun execute(@Body request: PuppeteerRequest): Call<PuppeteerResponse>
}