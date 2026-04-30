package com.jobhunter.ingestion.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.jobhunter.core.domain.JobPosting
import com.jobhunter.core.domain.ProcessingQueueRow
import com.jobhunter.core.domain.QueueState
import com.jobhunter.core.repository.JobPostingRepository
import com.jobhunter.core.repository.JobSourceRepository
import com.jobhunter.core.repository.ProcessingQueueRepository
import com.jobhunter.core.worker.QueueNotifier
import com.jobhunter.ingestion.client.ImapClient
import com.jobhunter.ingestion.dto.ParsedPostingDraft
import com.jobhunter.ingestion.parser.EmailParserRegistry
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

private val log = KotlinLogging.logger {}

data class IngestionRunResult(
    val emailsFetched: Int,
    val postingsCreated: Int,
)

@Service
class IngestionService(
    private val sources: JobSourceRepository,
    private val postings: JobPostingRepository,
    private val queue: ProcessingQueueRepository,
    private val imap: ImapClient,
    private val parsers: EmailParserRegistry,
    private val notifier: QueueNotifier,
    private val mapper: ObjectMapper,
) {

    @Transactional
    fun runSource(
        sourceCode: String,
        host: String, port: Int, username: String, password: String,
        maxMessages: Int,
    ): IngestionRunResult {
        val source = sources.findByCode(sourceCode)
            ?: error("Unknown source code: $sourceCode")
        val parser = parsers.byCode(sourceCode)
            ?: error("No parser registered for source $sourceCode")
        val cfg = mapper.readTree(source.config)
        val fromFilter = cfg["fromFilter"]?.asText()
            ?: error("Source $sourceCode missing fromFilter in config")
        val folder = cfg["folder"]?.asText() ?: "INBOX"

        var emailsFetched = 0
        var created = 0
        try {
            val emails = imap.fetch(host, port, username, password, folder, fromFilter, maxMessages)
            emailsFetched = emails.size
            for (email in emails) {
                val parsed = parser.parse(email)
                for (draft in parsed.postings) {
                    if (postings.findBySourceIdAndExternalId(source.id!!, draft.externalId) != null) {
                        continue
                    }
                    val saved = postings.save(toEntity(source.id!!, draft))
                    queue.save(ProcessingQueueRow(
                        jobPostingId = saved.id!!,
                        state = QueueState.INGESTED,
                    ))
                    created += 1
                }
            }
            source.lastRunAt = Instant.now()
            source.lastRunStatus = "OK"
            source.lastRunError = null
        } catch (e: Exception) {
            log.warn(e) { "Ingestion failed for $sourceCode" }
            source.lastRunAt = Instant.now()
            source.lastRunStatus = "FAILED"
            source.lastRunError = e.message?.take(2000)
            throw e
        } finally {
            sources.save(source)
        }
        if (created > 0) notifier.notify("queue_event")
        return IngestionRunResult(emailsFetched, created)
    }

    private fun toEntity(sourceId: Long, draft: ParsedPostingDraft): JobPosting = JobPosting(
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
