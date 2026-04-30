package com.jobhunter.ingestion.health

import com.jobhunter.core.domain.JobSource
import com.jobhunter.core.domain.SourceType
import com.jobhunter.core.repository.JobSourceRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.boot.actuate.health.Status
import java.time.Instant
import kotlin.test.assertEquals

class ImapHealthIndicatorTest {

    @Test
    fun `UP when at least one IMAP source ran successfully within 30 min`() {
        val sources = mockk<JobSourceRepository>()
        every { sources.findByEnabledTrue() } returns listOf(
            JobSource("IMAP_LINKEDIN_ALERTS", SourceType.IMAP, true, "{}",
                lastRunAt = Instant.now().minusSeconds(60),
                lastRunStatus = "OK", id = 1),
        )
        val health = ImapHealthIndicator(sources).health()
        assertEquals(Status.UP, health.status)
    }

    @Test
    fun `DOWN when last successful run is older than 30 min`() {
        val sources = mockk<JobSourceRepository>()
        every { sources.findByEnabledTrue() } returns listOf(
            JobSource("IMAP_LINKEDIN_ALERTS", SourceType.IMAP, true, "{}",
                lastRunAt = Instant.now().minusSeconds(60 * 60),
                lastRunStatus = "OK", id = 1),
        )
        val health = ImapHealthIndicator(sources).health()
        assertEquals(Status.DOWN, health.status)
    }

    @Test
    fun `UNKNOWN when never run`() {
        val sources = mockk<JobSourceRepository>()
        every { sources.findByEnabledTrue() } returns listOf(
            JobSource("IMAP_LINKEDIN_ALERTS", SourceType.IMAP, true, "{}", id = 1),
        )
        val health = ImapHealthIndicator(sources).health()
        assertEquals(Status.UNKNOWN, health.status)
    }
}
