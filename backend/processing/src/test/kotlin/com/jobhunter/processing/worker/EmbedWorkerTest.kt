package com.jobhunter.processing.worker

import com.jobhunter.core.AbstractRepositoryTest
import com.jobhunter.core.client.EmbeddingClient
import com.jobhunter.core.domain.*
import com.jobhunter.core.repository.*
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import java.time.Instant
import kotlin.test.assertEquals

class EmbedWorkerTest : AbstractRepositoryTest() {

    @Autowired lateinit var sources: JobSourceRepository
    @Autowired lateinit var postings: JobPostingRepository
    @Autowired lateinit var queue: ProcessingQueueRepository
    @Autowired lateinit var embeddings: PostingEmbeddingRepository
    @Autowired lateinit var txManager: PlatformTransactionManager

    @Test
    fun `embeds CLASSIFIED row, advances to EMBEDDED`() {
        val src = sources.save(JobSource("S", SourceType.IMAP, true, "{}"))
        val post = postings.save(JobPosting(
            sourceId = src.id!!, externalId = "E", rawText = "raw text", capturedAt = Instant.now(),
        ))
        val queueRow = queue.save(ProcessingQueueRow(jobPostingId = post.id!!, state = QueueState.CLASSIFIED))

        val embeddingClient = mockk<EmbeddingClient>()
        every { embeddingClient.embed("raw text") } returns FloatArray(1024) { 0.5f }

        val worker = EmbedWorker(queue, postings, embeddings, txManager, embeddingClient)
        worker.runOnce()

        assertEquals(QueueState.EMBEDDED, queue.findById(queueRow.id!!).get().state)
        val emb = embeddings.findById(post.id!!).get()
        assertEquals(1024, emb.embedding.size)
        assertEquals("bge-m3", emb.modelName)
    }
}
