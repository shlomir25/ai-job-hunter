package com.jobhunter.core.worker

import com.jobhunter.core.AbstractRepositoryTest
import com.jobhunter.core.domain.JobPosting
import com.jobhunter.core.domain.JobSource
import com.jobhunter.core.domain.ProcessingQueueRow
import com.jobhunter.core.domain.QueueState
import com.jobhunter.core.domain.SourceType
import com.jobhunter.core.repository.JobPostingRepository
import com.jobhunter.core.repository.JobSourceRepository
import com.jobhunter.core.repository.ProcessingQueueRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class QueueWorkerTest : AbstractRepositoryTest() {
  @Autowired lateinit var sources: JobSourceRepository

  @Autowired lateinit var postings: JobPostingRepository

  @Autowired lateinit var queue: ProcessingQueueRepository

  @Autowired lateinit var txManager: PlatformTransactionManager

  private fun seedRow(state: QueueState): ProcessingQueueRow {
    val src = sources.save(JobSource("S-${System.nanoTime()}", SourceType.IMAP, true, "{}"))
    val p = postings.save(
      JobPosting(
        sourceId = src.id!!,
        externalId = "E-${System.nanoTime()}",
        rawText = "x",
        capturedAt = Instant.now(),
      ),
    )
    return queue.save(ProcessingQueueRow(jobPostingId = p.id!!, state = state))
  }

  @Test
  fun `successful processing advances state`() {
    val row = seedRow(QueueState.INGESTED)
    val worker = TestWorker(
      input = QueueState.INGESTED,
      output = QueueState.PARSED,
      queue = queue,
      txManager = txManager,
      maxAttempts = 3,
    ) { /* succeed */ }

    worker.runOnce()

    val updated = queue.findById(row.id!!).get()
    assertEquals(QueueState.PARSED, updated.state)
    assertEquals(0, updated.attempts) // reset on advance
  }

  @Test
  fun `failure increments attempts and sets backoff`() {
    val row = seedRow(QueueState.INGESTED)
    val worker = TestWorker(
      input = QueueState.INGESTED,
      output = QueueState.PARSED,
      queue = queue,
      txManager = txManager,
      maxAttempts = 3,
    ) { throw RuntimeException("boom") }

    worker.runOnce()

    val updated = queue.findById(row.id!!).get()
    assertEquals(QueueState.INGESTED, updated.state)
    assertEquals(1, updated.attempts)
    assertEquals("boom", updated.lastError)
    assertNotNull(updated.nextAttemptAt)
  }

  @Test
  fun `exhausting attempts moves to FAILED`() {
    val row = seedRow(QueueState.INGESTED)
    val worker = TestWorker(
      input = QueueState.INGESTED,
      output = QueueState.PARSED,
      queue = queue,
      txManager = txManager,
      maxAttempts = 2,
    ) { throw RuntimeException("nope") }

    worker.runOnce() // attempt 1
    // bypass backoff for the test
    queue.findById(row.id!!).get().also {
      it.nextAttemptAt = null
      queue.save(it)
    }
    worker.runOnce() // attempt 2 — should now fail terminally

    val updated = queue.findById(row.id!!).get()
    assertEquals(QueueState.FAILED, updated.state)
    assertEquals(2, updated.attempts)
  }

  private class TestWorker(
    input: QueueState,
    output: QueueState,
    queue: ProcessingQueueRepository,
    txManager: PlatformTransactionManager,
    maxAttempts: Int,
    private val body: (Long) -> Unit,
  ) : QueueWorker(input, output, queue, txManager, maxAttempts) {
    override fun process(jobPostingId: Long) = body(jobPostingId)
  }
}
