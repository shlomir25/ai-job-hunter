package com.jobhunter.core.client

import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.embedding.EmbeddingRequest
import org.springframework.stereotype.Component

@Component
class OllamaEmbeddingClient(
    private val embeddingModel: EmbeddingModel,
) : EmbeddingClient {
    override fun embed(text: String): FloatArray {
        val response = embeddingModel.call(EmbeddingRequest(listOf(text), null))
        val first = response.results.firstOrNull()
            ?: error("Embedding model returned no results")
        return first.output
    }
}
