package com.jobhunter.core.repository

import com.jobhunter.core.AbstractRepositoryTest
import com.jobhunter.core.domain.JobSource
import com.jobhunter.core.domain.SourceType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class JobSourceRepositoryTest : AbstractRepositoryTest() {
    @Autowired lateinit var repository: JobSourceRepository

    @Test
    fun `saves and retrieves a job source with JSONB config`() {
        val source = JobSource(
            code = "IMAP_LINKEDIN",
            type = SourceType.IMAP,
            enabled = true,
            config = """{"folder":"INBOX","from":"jobs-noreply@linkedin.com"}""",
        )
        val saved = repository.save(source)
        assertNotNull(saved.id)

        val found = repository.findByCode("IMAP_LINKEDIN")
        assertNotNull(found)
        assertEquals(SourceType.IMAP, found.type)
        assertEquals(true, found.enabled)
    }

    @Test
    fun `code is unique`() {
        repository.save(JobSource("UNIQ", SourceType.SCRAPER, true, "{}"))
        val duplicate = JobSource("UNIQ", SourceType.SCRAPER, true, "{}")
        try {
            repository.saveAndFlush(duplicate)
            error("expected DataIntegrityViolationException")
        } catch (_: org.springframework.dao.DataIntegrityViolationException) {
            // expected
        }
    }
}
