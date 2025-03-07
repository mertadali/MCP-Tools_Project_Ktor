package com.example.mcp_server.chatbot.tools

import com.example.mcp_server.chatbot.protocol.MCPRequest
import com.example.mcp_server.chatbot.protocol.MCPResponse
import com.example.mcp_server.chatbot.protocol.MCPServerApi
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

class GithubMCPTools(
    baseUrl : String,
    private val apiKey : String) : MCPServerApi.BaseMCPTool("github",MCPServerApi.MCPToolType.GITHUB){

    override val actionNames = listOf(
        "getRepository",
        "listRepositories",
        "getBranches",
        "getCommits"
    )

    // Initialize trigger patterns for GitHub-related queries
    init {
        triggerPatterns.addAll(listOf(
            Regex("(fetch|get|pull|show|list)\\s+.*?(github|repo|repository)"),
            Regex("github\\s+.*?(repo|repository)"),
            Regex("my\\s+latest\\s+(repo|repository|code)"),
            Regex("latest\\s+(commit|branch|repo|code)\\s+on\\s+github")
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

    // Create Retrofit instance for GitHub MCP server

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    // Create API interface
    private val api = retrofit.create(GitHubServerApi::class.java)


    // Execute GitHub tool with given request
    override suspend fun execute(request: MCPRequest): MCPResponse {
        return try {
            val action = determineGitHubAction(request.query)
            val username = extractUsername(request.query)

            val gitHubRequest = GitHubRequest(
                action = action,
                username = username,
                apiKey = apiKey,
                repository = extractRepositoryName(request.query)
            )

            val response = withContext(Dispatchers.IO) {
                api.execute(gitHubRequest).execute()
            }

            if (response.isSuccessful) {
                response.body() ?: MCPResponse(
                    response = "Unable to get data from GitHub",
                    error = "Null response body"
                )
            } else {
                MCPResponse(
                    response = "Error fetching GitHub data: ${response.message()}",
                    error = response.errorBody()?.string()
                )
            }
        } catch (e: Exception) {
            MCPResponse(
                response = "An error occurred while fetching GitHub data: ${e.message}",
                error = e.stackTraceToString()
            )
        }
    }

    // Extract GitHub username from query
    private fun extractUsername(query: String): String {
        val forUserPattern = Regex("for\\s+user\\s+([\\w-]+)")
        val userPattern = Regex("(user|username|account)\\s+([\\w-]+)")
        val myRepoPattern = Regex("my\\s+(repo|repository|code)")

        // Return found username or default
        return when {
            myRepoPattern.containsMatchIn(query) -> "DEFAULT_USER" // Replace with authenticated user logic
            forUserPattern.containsMatchIn(query) -> forUserPattern.find(query)?.groupValues?.get(1) ?: "DEFAULT_USER"
            userPattern.containsMatchIn(query) -> userPattern.find(query)?.groupValues?.get(2) ?: "DEFAULT_USER"
            else -> "DEFAULT_USER" // Replace with authenticated user logic
        }
    }

    // Extract repository name from query if specified
    private fun extractRepositoryName(query: String): String? {
        val repoPattern = Regex("(repo|repository)\\s+named\\s+([\\w-]+)")
        val calledPattern = Regex("(repo|repository)\\s+called\\s+([\\w-]+)")

        return when {
            repoPattern.containsMatchIn(query) -> repoPattern.find(query)?.groupValues?.get(2)
            calledPattern.containsMatchIn(query) -> calledPattern.find(query)?.groupValues?.get(2)
            else -> null // Will list repositories instead of getting a specific one
        }
    }

    // Determine which GitHub action to perform based on query
    private fun determineGitHubAction(query: String): String {
        return when {
            query.contains("latest repo") || query.contains("latest repository") -> "listRepositories"
            Regex("(list|show)\\s+.*?(repos|repositories)").containsMatchIn(query) -> "listRepositories"
            Regex("(commits|changes)").containsMatchIn(query) -> "getCommits"
            Regex("(branches)").containsMatchIn(query) -> "getBranches"
            else -> "getRepository" // Default action
        }
    }
}

// GitHub-specific request model
data class GitHubRequest(
    val action: String,
    val username: String,
    val apiKey: String,
    val repository: String? = null,
    val branch: String? = null
)

// GitHub MCP server API interface
interface GitHubServerApi {
    @POST("github/execute")
    fun execute(@Body request: GitHubRequest): Call<MCPResponse>
}

// GitHub repository model
data class GitHubRepository(
    val name: String,
    val description: String?,
    val url: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("stargazers_count") val stars: Int,
    val language: String?
)

// GitHub direct API client (alternative implementation)
class GitHubDirectClient(private val token: String) {
    private val client = OkHttpClient()
    private val gson = Gson()

    suspend fun listUserRepositories(username: String): List<GitHubRepository> {
        val request = Request.Builder()
            .url("https://api.github.com/users/$username/repos?sort=updated")
            .header("Authorization", "token $token")
            .header("Accept", "application/vnd.github.v3+json")
            .build()

        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("GitHub API error: ${response.code}")

                val body = response.body?.string() ?: throw Exception("Empty response from GitHub")
                gson.fromJson(body, Array<GitHubRepository>::class.java).toList()
            }
        }
    }
}


