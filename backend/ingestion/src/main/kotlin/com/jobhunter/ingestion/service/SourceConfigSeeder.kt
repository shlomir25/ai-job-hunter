package com.jobhunter.ingestion.service

import com.jobhunter.core.domain.JobSource
import com.jobhunter.core.domain.SourceType
import com.jobhunter.core.repository.JobSourceRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class SourceConfigSeeder(private val sources: JobSourceRepository) : ApplicationRunner {

  override fun run(args: ApplicationArguments?) {
    val seeds = listOf(
      Triple("IMAP_LINKEDIN_ALERTS", SourceType.IMAP, """{"fromFilter":"@linkedin.com","folder":"INBOX"}"""),
      Triple("IMAP_INDEED_ALERTS", SourceType.IMAP, """{"fromFilter":"@indeed.com","folder":"INBOX"}"""),
      Triple("IMAP_GLASSDOOR_ALERTS", SourceType.IMAP, """{"fromFilter":"@glassdoor.com","folder":"INBOX"}"""),
      Triple("SCRAPER_ALLJOBS", SourceType.SCRAPER, """{"url":"https://www.alljobs.co.il/SearchResultsGuest.aspx"}"""),
      Triple("SCRAPER_JOBMASTER", SourceType.SCRAPER, """{"url":"https://www.jobmaster.co.il/jobs/"}"""),
    )
    for ((code, type, config) in seeds) {
      if (sources.findByCode(code) == null) {
        sources.save(
          JobSource(
            code = code,
            type = type,
            enabled = true,
            config = config,
          ),
        )
        log.info { "Seeded JobSource $code" }
      }
    }
  }
}
