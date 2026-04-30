package com.jobhunter.processing.worker

import com.jobhunter.core.client.EmbeddingClient
import com.jobhunter.core.domain.PostingEmbedding
import com.jobhunter.core.domain.QueueState
import com.jobhunter.core.repository.JobPostingRepository
import com.jobhunter.core.repository.PostingEmbeddingRepository
import com.jobhunter.core.repository.ProcessingQueueRepository
import com.jobhunter.core.worker.QueueWorker
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager

@Component
class EmbedWorker(
    queue: ProcessingQueueRepository,
    private val postings: JobPostingRepository,
    private val embeddings: PostingEmbeddingRepository,
    txManager: PlatformTransactionManager,
    private val embeddingClient: EmbeddingClient,
) : QueueWorker(
    inputState = QueueState.CLASSIFIED,
    outputState = QueueState.EMBEDDED,
    queue = queue,
    txManager = txManager,
    maxAttempts = 5,
    baseBackoffSeconds = 2,
    maxBackoffSeconds = 60,
) {
    override fun process(jobPostingId: Long) {
        val posting = postings.findById(jobPostingId).orElseThrow {
            IllegalStateException("Posting $jobPostingId not found")
        }
        val vec = embeddingClient.embed(posting.rawText)
        require(vec.size == 1024) { "Expected 1024-dim vector, got ${vec.size}" }
        embeddings.save(PostingEmbedding(
            jobPostingId = posting.id!!,
            embedding = vec,
            modelName = "bge-m3",
        ))
    }
}
