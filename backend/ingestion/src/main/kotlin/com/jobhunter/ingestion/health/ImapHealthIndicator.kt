package com.jobhunter.ingestion.health

import com.jobhunter.core.domain.SourceType
import com.jobhunter.core.repository.JobSourceRepository
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.actuate.health.Status
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

@Component("imap")
class ImapHealthIndicator(private val sources: JobSourceRepository) : HealthIndicator {

  override fun health(): Health {
    val imapSources = sources.findByEnabledTrue().filter { it.type == SourceType.IMAP }
    val recentOk = imapSources.any { src ->
      src.lastRunStatus == "OK" &&
        src.lastRunAt?.let { Duration.between(it, Instant.now()) < Duration.ofMinutes(30) } == true
    }
    val anyAttempted = imapSources.any { it.lastRunAt != null }
    return when {
      recentOk -> Health.up().withDetail("imapSources", imapSources.size).build()
      !anyAttempted -> Health.status(Status.UNKNOWN)
        .withDetail("reason", "no IMAP runs yet").build()
      else -> Health.down()
        .withDetail("reason", "no successful IMAP run within 30 min").build()
    }
  }
}
