package com.jobhunter.ingestion.scheduler

import com.jobhunter.core.domain.SourceType
import com.jobhunter.core.repository.JobSourceRepository
import com.jobhunter.ingestion.config.IngestionProperties
import com.jobhunter.ingestion.service.IngestionService
import com.jobhunter.ingestion.service.ScraperIngestionService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class IngestionScheduler(
  private val sources: JobSourceRepository,
  private val service: IngestionService,
  private val scraperService: ScraperIngestionService,
  private val props: IngestionProperties,
) {
  @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT30S")
  fun runImap() {
    if (props.username.isBlank() || props.password.isBlank()) {
      log.info { "IMAP credentials not configured; skipping IMAP run" }
      return
    }
    for (src in sources.findByEnabledTrue().filter { it.type == SourceType.IMAP }) {
      try {
        val result = service.runSource(
          sourceCode = src.code,
          host = props.host,
          port = props.port,
          username = props.username,
          password = props.password,
          maxMessages = props.maxMessagesPerRun,
        )
        log.info { "IMAP ${src.code}: fetched ${result.emailsFetched} emails, created ${result.postingsCreated} postings" }
      } catch (e: Exception) {
        log.warn(e) { "IMAP ${src.code} failed" }
      }
    }
  }

  @Scheduled(fixedDelayString = "PT60M", initialDelayString = "PT2M")
  fun runScrapers() {
    for (src in sources.findByEnabledTrue().filter { it.type == SourceType.SCRAPER }) {
      try {
        val result = scraperService.runSource(src.code)
        log.info { "Scraper ${src.code}: created ${result.postingsCreated} postings" }
      } catch (e: Exception) {
        log.warn(e) { "Scraper ${src.code} failed" }
      }
    }
  }
}
