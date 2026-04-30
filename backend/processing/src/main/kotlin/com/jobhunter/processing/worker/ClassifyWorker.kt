package com.jobhunter.processing.worker

import com.fasterxml.jackson.databind.ObjectMapper
import com.jobhunter.core.client.LlmClient
import com.jobhunter.core.domain.QueueState
import com.jobhunter.core.repository.JobPostingRepository
import com.jobhunter.core.repository.ProcessingQueueRepository
import com.jobhunter.core.worker.QueueWorker
import com.jobhunter.processing.config.ProcessingProperties
import com.jobhunter.processing.prompt.ClassifyPromptBuilder
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import java.time.Instant

@Component
class ClassifyWorker(
    private val queue: ProcessingQueueRepository,
    private val postings: JobPostingRepository,
    txManager: PlatformTransactionManager,
    private val llm: LlmClient,
    private val promptBuilder: ClassifyPromptBuilder,
    private val mapper: ObjectMapper,
    private val props: ProcessingProperties,
) : QueueWorker(
    inputState = QueueState.PARSED,
    outputState = QueueState.CLASSIFIED,
    queue = queue,
    txManager = txManager,
    maxAttempts = 3,
) {
    override fun process(jobPostingId: Long) {
        val posting = postings.findById(jobPostingId).orElseThrow {
            IllegalStateException("Posting $jobPostingId not found")
        }
        val result = promptBuilder.invoke(llm, posting.rawText, mapper)
        posting.categories = result.categories
        postings.save(posting)

        val terminal: QueueState? = when {
            result.categories.isEmpty() -> QueueState.IRRELEVANT
            result.categories.none { it in props.monitoredCategories } -> QueueState.OUT_OF_SCOPE
            else -> null
        }
        if (terminal != null) {
            val row = queue.findAll().first { it.jobPostingId == jobPostingId && it.state == QueueState.PARSED }
            row.state = terminal
            row.attempts = 0
            row.lastError = null
            row.nextAttemptAt = null
            row.updatedAt = Instant.now()
            queue.save(row)
        }
    }
}
