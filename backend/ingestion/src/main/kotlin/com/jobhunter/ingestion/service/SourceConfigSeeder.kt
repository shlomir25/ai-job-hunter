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
            "IMAP_LINKEDIN_ALERTS"  to """{"fromFilter":"@linkedin.com","folder":"INBOX"}""",
            "IMAP_INDEED_ALERTS"    to """{"fromFilter":"@indeed.com","folder":"INBOX"}""",
            "IMAP_GLASSDOOR_ALERTS" to """{"fromFilter":"@glassdoor.com","folder":"INBOX"}""",
        )
        for ((code, config) in seeds) {
            if (sources.findByCode(code) == null) {
                sources.save(JobSource(
                    code = code,
                    type = SourceType.IMAP,
                    enabled = true,
                    config = config,
                ))
                log.info { "Seeded JobSource $code" }
            }
        }
    }
}
