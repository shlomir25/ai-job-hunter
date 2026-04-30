package com.jobhunter.ingestion.scheduler

import com.jobhunter.core.domain.JobSource
import com.jobhunter.core.domain.SourceType
import com.jobhunter.core.repository.JobSourceRepository
import com.jobhunter.ingestion.config.IngestionProperties
import com.jobhunter.ingestion.service.IngestionRunResult
import com.jobhunter.ingestion.service.IngestionService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class IngestionSchedulerTest {
  @Test
  fun `runs all enabled IMAP sources`() {
    val sources = mockk<JobSourceRepository>()
    val service = mockk<IngestionService>()
    val enabled = listOf(
      JobSource("IMAP_LINKEDIN_ALERTS", SourceType.IMAP, true, "{}", id = 1),
      JobSource("IMAP_INDEED_ALERTS", SourceType.IMAP, true, "{}", id = 2),
    )
    val disabled = JobSource("IMAP_GLASSDOOR_ALERTS", SourceType.IMAP, false, "{}", id = 3)
    every { sources.findByEnabledTrue() } returns (enabled + disabled).filter { it.enabled }
    every { service.runSource(any(), any(), any(), any(), any(), any()) } returns IngestionRunResult(0, 0)

    val props = IngestionProperties(
      host = "imap.gmail.com",
      port = 993,
      username = "u",
      password = "p",
      maxMessagesPerRun = 50,
    )
    val scheduler = IngestionScheduler(sources, service, props)
    scheduler.run()

    verify { service.runSource("IMAP_LINKEDIN_ALERTS", "imap.gmail.com", 993, "u", "p", 50) }
    verify { service.runSource("IMAP_INDEED_ALERTS", "imap.gmail.com", 993, "u", "p", 50) }
    verify(exactly = 0) { service.runSource("IMAP_GLASSDOOR_ALERTS", any(), any(), any(), any(), any()) }
  }

  @Test
  fun `continues when one source throws`() {
    val sources = mockk<JobSourceRepository>()
    val service = mockk<IngestionService>()
    every { sources.findByEnabledTrue() } returns listOf(
      JobSource("A", SourceType.IMAP, true, "{}", id = 1),
      JobSource("B", SourceType.IMAP, true, "{}", id = 2),
    )
    every { service.runSource("A", any(), any(), any(), any(), any()) } throws RuntimeException("bad")
    every { service.runSource("B", any(), any(), any(), any(), any()) } returns IngestionRunResult(0, 0)

    val props = IngestionProperties("h", 0, "u", "p", 1)
    IngestionScheduler(sources, service, props).run()

    verify { service.runSource("B", any(), any(), any(), any(), any()) }
  }
}
