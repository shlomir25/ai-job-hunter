package com.jobhunter.app.health

import com.jobhunter.core.client.LlmClient
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component

@Component("ollama")
class OllamaHealthIndicator(private val llm: LlmClient) : HealthIndicator {
    override fun health(): Health = try {
        llm.chat(system = "Reply with the single word OK.", user = "ping")
        Health.up().build()
    } catch (e: Exception) {
        Health.down().withDetail("error", e.message ?: "unknown").build()
    }
}
