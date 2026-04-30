package com.jobhunter.core.repository

import com.jobhunter.core.AbstractRepositoryTest
import com.jobhunter.core.domain.JobPosting
import com.jobhunter.core.domain.JobSource
import com.jobhunter.core.domain.ProcessingQueueRow
import com.jobhunter.core.domain.QueueState
import com.jobhunter.core.domain.SourceType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import kotlin.test.assertEquals

class ProcessingQueueRepositoryTest : AbstractRepositoryTest() {
  @Autowired lateinit var sources: JobSourceRepository

  @Autowired lateinit var postings: JobPostingRepository

  @Autowired lateinit var queue: ProcessingQueueRepository

  @Test
  fun `claims oldest row in target state`() {
    val source = sources.save(JobSource("S", SourceType.IMAP, true, "{}"))
    repeat(3) { i ->
      val p = postings.save(
        JobPosting(
          sourceId = source.id!!,
          externalId = "E$i",
          rawText = "x",
          capturedAt = Instant.now(),
        ),
      )
      queue.save(ProcessingQueueRow(jobPostingId = p.id!!, state = QueueState.INGESTED))
    }
    val claimed = queue.claimNext(QueueState.INGESTED.name, 2)
    assertEquals(2, claimed.size)
  }

  @Test
  fun `respects next_attempt_at backoff`() {
    val source = sources.save(JobSource("S2", SourceType.IMAP, true, "{}"))
    val p = postings.save(
      JobPosting(
        sourceId = source.id!!,
        externalId = "X",
        rawText = "x",
        capturedAt = Instant.now(),
      ),
    )
    queue.save(
      ProcessingQueueRow(
        jobPostingId = p.id!!,
        state = QueueState.INGESTED,
        nextAttemptAt = Instant.now().plusSeconds(60),
      ),
    )
    val claimed = queue.claimNext(QueueState.INGESTED.name, 5)
    assertEquals(0, claimed.size)
  }
}
