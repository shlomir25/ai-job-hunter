package com.jobhunter.core.client

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.embedding.EmbeddingRequest
import org.springframework.ai.embedding.EmbeddingResponse
import org.springframework.ai.embedding.Embedding
import kotlin.test.assertEquals

class OllamaEmbeddingClientTest {

    @Test
    fun `embed returns vector from underlying model`() {
        val model = mockk<EmbeddingModel>()
        val expected = floatArrayOf(0.1f, 0.2f, 0.3f)
        every { model.call(any<EmbeddingRequest>()) } returns EmbeddingResponse(
            listOf(Embedding(expected, 0)),
        )

        val client = OllamaEmbeddingClient(model)
        val result = client.embed("hello")
        assertEquals(expected.toList(), result.toList())
    }

    @Test
    fun `embed throws when model returns empty`() {
        val model = mockk<EmbeddingModel>()
        every { model.call(any<EmbeddingRequest>()) } returns EmbeddingResponse(emptyList())
        val client = OllamaEmbeddingClient(model)
        try {
            client.embed("hi")
            error("expected IllegalStateException")
        } catch (_: IllegalStateException) {
            // expected
        }
    }
}
