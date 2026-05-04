package com.jobhunter.ingestion.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.jobhunter.core.domain.JobPosting
import com.jobhunter.core.domain.ProcessingQueueRow
import com.jobhunter.core.domain.QueueState
import com.jobhunter.core.repository.JobPostingRepository
import com.jobhunter.core.repository.JobSourceRepository
import com.jobhunter.core.repository.ProcessingQueueRepository
import com.jobhunter.core.worker.QueueNotifier
import com.jobhunter.ingestion.dto.ParsedPostingDraft
import com.jobhunter.ingestion.scraper.HttpScraperRegistry
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

private val log = KotlinLogging.logger {}

@Service
class ScraperIngestionService(
  private val sources: JobSourceRepository,
  private val postings: JobPostingRepository,
  private val queue: ProcessingQueueRepository,
  private val scrapers: HttpScraperRegistry,
  private val notifier: QueueNotifier,
  private val mapper: ObjectMapper,
) {

  @Transactional
  fun runSource(sourceCode: String): IngestionRunResult {
    val source = sources.findByCode(sourceCode)
      ?: error("Unknown source code: $sourceCode")
    val scraper = scrapers.byCode(sourceCode)
      ?: error("No scraper registered for source $sourceCode")

    var created = 0
    try {
      val cfg: Map<String, String> = mapper.readValue(source.config)
      val drafts = scraper.scrape(cfg)
      for (draft in drafts) {
        if (postings.findBySourceIdAndExternalId(source.id!!, draft.externalId) != null) continue
        val saved = postings.save(toEntity(source.id!!, draft))
        queue.save(ProcessingQueueRow(jobPostingId = saved.id!!, state = QueueState.INGESTED))
        created += 1
      }
      source.lastRunAt = Instant.now()
      source.lastRunStatus = "OK"
      source.lastRunError = null
    } catch (e: Exception) {
      log.warn(e) { "Scraper run failed for $sourceCode" }
      source.lastRunAt = Instant.now()
      source.lastRunStatus = "FAILED"
      source.lastRunError = e.message?.take(2000)
      throw e
    } finally {
      sources.save(source)
    }
    if (created > 0) notifier.notify("queue_event")
    return IngestionRunResult(emailsFetched = 0, postingsCreated = created)
  }

  private fun toEntity(sourceId: Long, draft: ParsedPostingDraft): JobPosting =
    JobPosting(
      sourceId = sourceId,
      externalId = draft.externalId,
      rawText = draft.rawText,
      rawHtml = draft.rawHtml,
      sourceUrl = draft.sourceUrl,
      title = draft.title,
      company = draft.company,
      location = draft.location,
      postedAt = draft.postedAt,
      capturedAt = Instant.now(),
    )
}
