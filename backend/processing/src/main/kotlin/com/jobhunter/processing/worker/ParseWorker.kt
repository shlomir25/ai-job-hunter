package com.jobhunter.processing.worker

import com.fasterxml.jackson.databind.ObjectMapper
import com.jobhunter.core.client.LlmClient
import com.jobhunter.core.domain.QueueState
import com.jobhunter.core.repository.JobPostingRepository
import com.jobhunter.core.repository.ProcessingQueueRepository
import com.jobhunter.core.worker.QueueWorker
import com.jobhunter.processing.prompt.ParsePromptBuilder
import com.jobhunter.processing.service.EmailExtractor
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager

@Component
class ParseWorker(
    queue: ProcessingQueueRepository,
    private val postings: JobPostingRepository,
    txManager: PlatformTransactionManager,
    private val llm: LlmClient,
    private val promptBuilder: ParsePromptBuilder,
    private val emailExtractor: EmailExtractor,
    private val mapper: ObjectMapper,
) : QueueWorker(
    inputState = QueueState.INGESTED,
    outputState = QueueState.PARSED,
    queue = queue,
    txManager = txManager,
    maxAttempts = 3,
) {
    override fun process(jobPostingId: Long) {
        val posting = postings.findById(jobPostingId).orElseThrow {
            IllegalStateException("Posting $jobPostingId not found")
        }
        val parsed = promptBuilder.invoke(llm, posting.rawText, mapper)
        posting.title = parsed.title ?: posting.title
        posting.company = parsed.company ?: posting.company
        posting.location = parsed.location ?: posting.location
        posting.isRemote = parsed.isRemote ?: posting.isRemote
        posting.language = parsed.language ?: posting.language
        posting.description = parsed.description ?: posting.description
        posting.requirements = parsed.requirements ?: posting.requirements
        posting.salaryText = parsed.salaryText ?: posting.salaryText
        posting.applyUrl = parsed.applyUrl ?: posting.applyUrl

        // Email is regex-first, with the LLM's contactEmail used only as a hint to the fallback path.
        val emailFromText = emailExtractor.extract(posting.rawText, posting.company)
        posting.contactEmail = emailFromText ?: parsed.contactEmail?.takeIf { it.matches(EMAIL_REGEX) }

        postings.save(posting)
    }

    companion object {
        private val EMAIL_REGEX = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    }
}
