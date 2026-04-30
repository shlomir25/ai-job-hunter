package com.jobhunter.ingestion.scheduler

import com.jobhunter.core.domain.SourceType
import com.jobhunter.core.repository.JobSourceRepository
import com.jobhunter.ingestion.config.IngestionProperties
import com.jobhunter.ingestion.service.IngestionService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class IngestionScheduler(
  private val sources: JobSourceRepository,
  private val service: IngestionService,
  private val props: IngestionProperties,
) {
  @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT30S")
  fun run() {
    if (props.username.isBlank() || props.password.isBlank()) {
      log.info { "IMAP credentials not configured; skipping scheduled ingestion run" }
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
        log.info { "Source ${src.code}: fetched ${result.emailsFetched} emails, created ${result.postingsCreated} postings" }
      } catch (e: Exception) {
        log.warn(e) { "Source ${src.code} run failed" }
      }
    }
  }
}
