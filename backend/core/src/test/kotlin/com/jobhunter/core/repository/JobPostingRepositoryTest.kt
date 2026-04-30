package com.jobhunter.core.repository

import com.jobhunter.core.AbstractRepositoryTest
import com.jobhunter.core.domain.Category
import com.jobhunter.core.domain.JobPosting
import com.jobhunter.core.domain.JobSource
import com.jobhunter.core.domain.SourceType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JobPostingRepositoryTest : AbstractRepositoryTest() {
    @Autowired lateinit var sources: JobSourceRepository
    @Autowired lateinit var postings: JobPostingRepository

    @Test
    fun `saves and retrieves a posting with categories`() {
        val source = sources.save(JobSource("S1", SourceType.IMAP, true, "{}"))
        val posting = JobPosting(
            sourceId = source.id!!,
            externalId = "EXT-1",
            rawText = "We need a Kotlin engineer.",
            title = "Backend Engineer",
            company = "Acme",
            language = "en",
            contactEmail = "jobs@acme.com",
            categories = listOf(Category.SOFTWARE_BACKEND),
            capturedAt = Instant.now(),
        )
        val saved = postings.save(posting)
        assertNotNull(saved.id)

        val found = postings.findById(saved.id!!).get()
        assertEquals("EXT-1", found.externalId)
        assertEquals(listOf(Category.SOFTWARE_BACKEND), found.categories)
        assertEquals("jobs@acme.com", found.contactEmail)
    }

    @Test
    fun `unique source plus external id is enforced`() {
        val source = sources.save(JobSource("S2", SourceType.SCRAPER, true, "{}"))
        postings.save(JobPosting(
            sourceId = source.id!!, externalId = "DUP", rawText = "x", capturedAt = Instant.now(),
        ))
        try {
            postings.saveAndFlush(JobPosting(
                sourceId = source.id!!, externalId = "DUP", rawText = "y", capturedAt = Instant.now(),
            ))
            error("expected DataIntegrityViolationException")
        } catch (_: org.springframework.dao.DataIntegrityViolationException) {
            // expected
        }
    }

    @Test
    fun `findBySourceIdAndExternalId returns existing or null`() {
        val source = sources.save(JobSource("S3", SourceType.IMAP, true, "{}"))
        postings.save(JobPosting(
            sourceId = source.id!!, externalId = "FIND-ME", rawText = "x", capturedAt = Instant.now(),
        ))
        assertNotNull(postings.findBySourceIdAndExternalId(source.id!!, "FIND-ME"))
        assertTrue(postings.findBySourceIdAndExternalId(source.id!!, "MISSING") == null)
    }
}
