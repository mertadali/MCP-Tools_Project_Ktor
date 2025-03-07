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

// Brave Search MCP tool implementation
class BraveSearchMCPTool(
    baseUrl: String,
    private val apiKey: String
) : MCPServerApi.BaseMCPTool("brave-search", MCPServerApi.MCPToolType.BRAVE_SEARCH) {

    override val actionNames = listOf(
        "search",
        "imageSearch",
        "newsSearch"
    )

    // Initialize trigger patterns for search-related queries
    init {
        triggerPatterns.addAll(listOf(
            Regex("(search|find|look up|google|search for)\\s+(.+)"),
            Regex("(what is|who is|tell me about)\\s+(.+)"),
            Regex("find\\s+news\\s+about\\s+(.+)"),
            Regex("search\\s+for\\s+images\\s+of\\s+(.+)")
        ))
    }

    // Create HTTP client with logging
    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Create Retrofit instance for Brave Search MCP server
    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    // Create API interface
    private val api = retrofit.create(BraveSearchApi::class.java)

    // Execute Brave Search tool with given request
    override suspend fun execute(request: MCPRequest): MCPResponse {
        return try {
            val searchType = determineSearchType(request.query)
            val searchQuery = extractSearchQuery(request.query)

            val searchRequest = BraveSearchRequest(
                query = searchQuery,
                type = searchType,
                apiKey = apiKey,
                count = 5 // Default number of results
            )

            val response = withContext(Dispatchers.IO) {
                api.search(searchRequest).execute()
            }

            if (response.isSuccessful) {
                val searchResults = response.body()

                if (searchResults != null) {
                    // Format the search results into a nice response
                    val formattedResponse = formatSearchResults(searchResults, searchType)
                    MCPResponse(response = formattedResponse)
                } else {
                    MCPResponse(
                        response = "I searched for '$searchQuery' but didn't find any results.",
                        error = "Null response body"
                    )
                }
            } else {
                MCPResponse(
                    response = "Error searching for '$searchQuery': ${response.message()}",
                    error = response.errorBody()?.string()
                )
            }
        } catch (e: Exception) {
            MCPResponse(
                response = "An error occurred while searching: ${e.message}",
                error = e.stackTraceToString()
            )
        }
    }

    // Extract search query from user input
    private fun extractSearchQuery(query: String): String {
        // Match different search patterns
        val searchForPattern = Regex("search\\s+for\\s+(.+)", RegexOption.IGNORE_CASE)
        val searchPattern = Regex("search\\s+(.+)", RegexOption.IGNORE_CASE)
        val findPattern = Regex("find\\s+(.+)", RegexOption.IGNORE_CASE)
        val lookUpPattern = Regex("look\\s+up\\s+(.+)", RegexOption.IGNORE_CASE)
        val whatIsPattern = Regex("what\\s+is\\s+(.+)", RegexOption.IGNORE_CASE)
        val whoIsPattern = Regex("who\\s+is\\s+(.+)", RegexOption.IGNORE_CASE)
        val tellMeAboutPattern = Regex("tell\\s+me\\s+about\\s+(.+)", RegexOption.IGNORE_CASE)

        return when {
            searchForPattern.containsMatchIn(query) -> searchForPattern.find(query)?.groupValues?.get(1) ?: query
            searchPattern.containsMatchIn(query) -> searchPattern.find(query)?.groupValues?.get(1) ?: query
            findPattern.containsMatchIn(query) -> findPattern.find(query)?.groupValues?.get(1) ?: query
            lookUpPattern.containsMatchIn(query) -> lookUpPattern.find(query)?.groupValues?.get(1) ?: query
            whatIsPattern.containsMatchIn(query) -> whatIsPattern.find(query)?.groupValues?.get(1) ?: query
            whoIsPattern.containsMatchIn(query) -> whoIsPattern.find(query)?.groupValues?.get(1) ?: query
            tellMeAboutPattern.containsMatchIn(query) -> tellMeAboutPattern.find(query)?.groupValues?.get(1) ?: query
            else -> query
        }
    }

    // Determine type of search based on query
    private fun determineSearchType(query: String): String {
        return when {
            Regex("image(s)?\\s+of", RegexOption.IGNORE_CASE).containsMatchIn(query) -> "imageSearch"
            Regex("(news|articles|recent events)\\s+about", RegexOption.IGNORE_CASE).containsMatchIn(query) -> "newsSearch"
            else -> "search" // Default to web search
        }
    }

    // Format search results based on search type
    private fun formatSearchResults(results: BraveSearchResponse, type: String): String {
        val sb = StringBuilder()

        when (type) {
            "imageSearch" -> {
                sb.appendLine("Here are some image results:")
                results.images?.forEach { image ->
                    sb.appendLine("- ${image.title}: ${image.url}")
                }
            }
            "newsSearch" -> {
                sb.appendLine("Here are some news results:")
                results.news?.forEach { newsItem ->
                    sb.appendLine("- ${newsItem.title} (${newsItem.source}): ${newsItem.url}")
                    sb.appendLine("  Published: ${newsItem.publishedTime}")
                    sb.appendLine("  ${newsItem.description}")
                    sb.appendLine()
                }
            }
            else -> {
                sb.appendLine("Here are search results:")
                results.webPages?.forEach { webPage ->
                    sb.appendLine("- ${webPage.title}")
                    sb.appendLine("  ${webPage.url}")
                    sb.appendLine("  ${webPage.snippet}")
                    sb.appendLine()
                }
            }
        }

        return sb.toString()
    }
}

// Brave Search-specific request model
data class BraveSearchRequest(
    val query: String,
    val type: String = "search",
    val apiKey: String,
    val count: Int = 5,
    val market: String = "en-US",
    val safeSearch: String = "Moderate"
)

// Brave Search-specific response models
data class BraveSearchResponse(
    val webPages: List<WebPage>? = null,
    val images: List<ImageResult>? = null,
    val news: List<NewsResult>? = null,
    val relatedSearches: List<String>? = null,
    @SerializedName("_type") val type: String
)

data class WebPage(
    val name: String,
    val url: String,
    val title: String,
    val snippet: String,
    @SerializedName("dateLastCrawled") val lastCrawled: String
)

data class ImageResult(
    val url: String,
    val height: Int,
    val width: Int,
    val title: String,
    val hostPageUrl: String
)

data class NewsResult(
    val url: String,
    val title: String,
    val description: String,
    val source: String,
    @SerializedName("datePublished") val publishedTime: String
)

// Brave Search MCP server API interface
interface BraveSearchApi {
    @POST("brave-search/execute")
    fun search(@Body request: BraveSearchRequest): Call<BraveSearchResponse>
}