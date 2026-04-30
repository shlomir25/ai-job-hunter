package com.jobhunter.ingestion.service

import com.jobhunter.core.AbstractRepositoryTest
import com.jobhunter.core.repository.JobSourceRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourceConfigSeederTest : AbstractRepositoryTest() {
    @Autowired lateinit var sources: JobSourceRepository

    @Test
    fun `seed creates three IMAP sources when none exist`() {
        val seeder = SourceConfigSeeder(sources)
        seeder.run(null)
        val codes = sources.findAll().map { it.code }.toSet()
        assertTrue(codes.contains("IMAP_LINKEDIN_ALERTS"))
        assertTrue(codes.contains("IMAP_INDEED_ALERTS"))
        assertTrue(codes.contains("IMAP_GLASSDOOR_ALERTS"))
        assertEquals(3, codes.size)
    }

    @Test
    fun `seed is idempotent - running twice does not duplicate`() {
        val seeder = SourceConfigSeeder(sources)
        seeder.run(null)
        seeder.run(null)
        assertEquals(3L, sources.count())
    }
}
