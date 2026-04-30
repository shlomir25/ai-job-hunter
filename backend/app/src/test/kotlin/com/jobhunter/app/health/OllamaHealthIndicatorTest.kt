package com.jobhunter.app.health

import com.jobhunter.core.client.LlmClient
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.boot.actuate.health.Status
import kotlin.test.assertEquals

class OllamaHealthIndicatorTest {

    @Test
    fun `UP when llm responds`() {
        val llm = mockk<LlmClient>()
        every { llm.chat(any(), any()) } returns "ok"
        val health = OllamaHealthIndicator(llm).health()
        assertEquals(Status.UP, health.status)
    }

    @Test
    fun `DOWN when llm throws`() {
        val llm = mockk<LlmClient>()
        every { llm.chat(any(), any()) } throws RuntimeException("connection refused")
        val health = OllamaHealthIndicator(llm).health()
        assertEquals(Status.DOWN, health.status)
        assertEquals("connection refused", health.details["error"])
    }
}
