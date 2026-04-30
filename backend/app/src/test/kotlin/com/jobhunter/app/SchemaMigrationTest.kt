package com.jobhunter.app

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals

@SpringBootTest
@Testcontainers
class SchemaMigrationTest {
    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("pgvector/pgvector:pg16")
            .withDatabaseName("jobhunter")
            .withUsername("jobhunter")
            .withPassword("jobhunter")

        @DynamicPropertySource
        @JvmStatic
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.ai.ollama.base-url") { "http://disabled" }
        }
    }

    @Autowired lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `migration creates all expected tables`() {
        val tables = jdbcTemplate.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name",
            String::class.java,
        )
        val expected = listOf(
            "cv",
            "email_send_record",
            "flyway_schema_history",
            "job_posting",
            "job_source",
            "match",
            "posting_embedding",
            "processing_queue",
        )
        assertEquals(expected, tables)
    }

    @Test
    fun `pgvector extension is installed`() {
        val count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM pg_extension WHERE extname = 'vector'",
            Int::class.java,
        )
        assertEquals(1, count)
    }
}
