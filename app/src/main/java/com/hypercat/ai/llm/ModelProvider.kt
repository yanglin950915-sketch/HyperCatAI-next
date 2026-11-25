package com.hypercat.ai.llm

enum class ModelMode { LOCAL, ONLINE }

data class ModelConfiguration(
    val mode: ModelMode = ModelMode.ONLINE,
    val endpoint: String = "",
    val modelName: String = "",
    val hasApiKey: Boolean = false,
    val localModelPath: String = ""
)

data class ModelMessage(val role: String, val content: String)

interface ModelProvider {
    val mode: ModelMode
    suspend fun generate(messages: List<ModelMessage>): Result<String>
}

/**
 * Provider contracts only. Concrete network/local runtimes are added in later milestones.
 * API keys must be stored in encrypted local storage and never committed to source control.
 */
