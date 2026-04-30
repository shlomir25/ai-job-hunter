package com.jobhunter.app

import com.jobhunter.core.client.LlmClient
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SmokeTest {

  companion object {
    @Container
    @JvmStatic
    val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("pgvector/pgvector:pg16")
      .withDatabaseName("jobhunter")
      .withUsername("jobhunter")
      .withPassword("jobhunter")

    @DynamicPropertySource
    @JvmStatic
    fun props(registry: DynamicPropertyRegistry) {
      registry.add("spring.datasource.url") { postgres.jdbcUrl }
      registry.add("spring.datasource.username") { postgres.username }
      registry.add("spring.datasource.password") { postgres.password }
      // Disable real Ollama auto-config; we provide a mock LlmClient.
      registry.add("spring.ai.ollama.base-url") { "http://disabled" }
    }
  }

  @LocalServerPort var port: Int = 0

  @Autowired lateinit var rest: RestTemplateBuilder

  @TestConfiguration
  class MockLlm {
    @Bean
    @Primary
    fun llmClient(): LlmClient = mockk<LlmClient>().apply {
      every { chat(any(), any()) } returns "ok"
      every { chatStructured(any(), any()) } returns "{}"
    }
  }

  @Test
  fun `actuator health is up`() {
    val template = rest.build()
    val response = template.getForEntity("http://localhost:$port/actuator/health", String::class.java)
    assertEquals(200, response.statusCode.value())
    assertTrue(response.body!!.contains("\"status\":\"UP\""))
  }
}
