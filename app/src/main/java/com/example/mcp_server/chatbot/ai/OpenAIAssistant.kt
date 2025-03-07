package com.example.mcp_server.chatbot.ai

import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.chat.ChatCompletion
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow


@OptIn(BetaOpenAI::class)

// OpenAI Assistant service for chat completions
class OpenAIAssistant(private val apiKey: String) {

    // Initialize OpenAI client
    private val client by lazy {
        OpenAI(apiKey)
    }

    // Default model ID (can be changed as needed)
    private val defaultModel = ModelId("gpt-4")

    // Conversation history
    private val conversationHistory = mutableListOf<ChatMessage>()

    // System instructions for the assistant
    private val systemInstructions = """
        You are a helpful assistant integrated with various tools. 
        When users ask for information from GitHub, web browsing, or search results, 
        you'll use appropriate tools to fetch that information.
        
        For general questions, provide helpful, concise, and accurate answers.
        For tool-specific requests, respond accordingly after the tool has provided the information.
    """.trimIndent()

    init {
        // Add system message to conversation history
        conversationHistory.add(ChatMessage(
            role = ChatRole.System,
            content = systemInstructions
        ))
    }

    // Get chat completion from OpenAI
    suspend fun getChatCompletion(userMessage: String): Flow<String> = flow {
        // Add user message to conversation history
        conversationHistory.add(ChatMessage(
            role = ChatRole.User,
            content = userMessage
        ))

        // Create request with conversation history
        val completionRequest = ChatCompletionRequest(
            model = defaultModel,
            messages = conversationHistory
        )

        // Get completion from OpenAI
        val completion: ChatCompletion = client.chatCompletion(completionRequest)

        // Extract assistant message
        val assistantMessage = completion.choices.first().message?.content ?: "Sorry, I couldn't generate a response."

        // Add assistant message to conversation history
        conversationHistory.add(ChatMessage(
            role = ChatRole.Assistant,
            content = assistantMessage
        ))

        // Emit the response
        emit(assistantMessage)
    }

    // Add tool response to conversation history
    fun addToolResponse(toolResponse: String) {
        // Add as a system message to provide context
        conversationHistory.add(ChatMessage(
            role = ChatRole.System,
            content = "Tool response: $toolResponse"
        ))
    }

    // Clear conversation history (except system instructions)
    fun clearConversation() {
        conversationHistory.clear()
        conversationHistory.add(ChatMessage(
            role = ChatRole.System,
            content = systemInstructions
        ))
    }
}