package com.jobhunter.processing.worker

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.jobhunter.core.AbstractRepositoryTest
import com.jobhunter.core.domain.Category
import com.jobhunter.core.domain.JobPosting
import com.jobhunter.core.domain.JobSource
import com.jobhunter.core.domain.ProcessingQueueRow
import com.jobhunter.core.domain.QueueState
import com.jobhunter.core.domain.SourceType
import com.jobhunter.core.repository.JobPostingRepository
import com.jobhunter.core.repository.JobSourceRepository
import com.jobhunter.core.repository.ProcessingQueueRepository
import com.jobhunter.processing.client.RecordingLlmClient
import com.jobhunter.processing.config.ProcessingProperties
import com.jobhunter.processing.prompt.ClassifyPromptBuilder
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.ContextConfiguration
import org.springframework.transaction.PlatformTransactionManager
import java.time.Instant
import kotlin.test.assertEquals

@ContextConfiguration(classes = [ClassifyWorkerTest.TestBeans::class])
class ClassifyWorkerTest : AbstractRepositoryTest() {

  @TestConfiguration
  class TestBeans {
    @Bean fun mapper() = ObjectMapper().registerKotlinModule()

    @Bean fun classifyPromptBuilder() = ClassifyPromptBuilder()
  }

  @Autowired lateinit var sources: JobSourceRepository

  @Autowired lateinit var postings: JobPostingRepository

  @Autowired lateinit var queue: ProcessingQueueRepository

  @Autowired lateinit var txManager: PlatformTransactionManager

  @Autowired lateinit var mapper: ObjectMapper

  @Autowired lateinit var prompt: ClassifyPromptBuilder

  private fun seed(state: QueueState, body: String): Pair<JobPosting, ProcessingQueueRow> {
    val src = sources.save(JobSource("S-${System.nanoTime()}", SourceType.IMAP, true, "{}"))
    val p = postings.save(
      JobPosting(
        sourceId = src.id!!,
        externalId = "E-${System.nanoTime()}",
        rawText = body,
        capturedAt = Instant.now(),
      ),
    )
    val r = queue.save(ProcessingQueueRow(jobPostingId = p.id!!, state = state))
    return p to r
  }

  @Test
  fun `monitored category advances row to CLASSIFIED and stores categories`() {
    val (post, queueRow) = seed(QueueState.PARSED, "Backend engineer Kotlin")
    val llm = RecordingLlmClient()
    llm.recordByUser(post.rawText, """["SOFTWARE_BACKEND"]""")

    val props = ProcessingProperties(monitoredCategories = listOf(Category.SOFTWARE_BACKEND))
    val worker = ClassifyWorker(queue, postings, txManager, llm, prompt, mapper, props)
    worker.runOnce()

    assertEquals(QueueState.CLASSIFIED, queue.findById(queueRow.id!!).get().state)
    assertEquals(listOf(Category.SOFTWARE_BACKEND), postings.findById(post.id!!).get().categories)
  }

  @Test
  fun `non-monitored category routes row to OUT_OF_SCOPE`() {
    val (_, queueRow) = seed(QueueState.PARSED, "Marketing manager")
    val llm = RecordingLlmClient()
    llm.recordByUser("Marketing manager", """["MARKETING"]""")

    val props = ProcessingProperties(monitoredCategories = listOf(Category.SOFTWARE_BACKEND))
    ClassifyWorker(queue, postings, txManager, llm, prompt, mapper, props).runOnce()

    assertEquals(QueueState.OUT_OF_SCOPE, queue.findById(queueRow.id!!).get().state)
  }

  @Test
  fun `empty classification routes row to IRRELEVANT`() {
    val (_, queueRow) = seed(QueueState.PARSED, "Garbage")
    val llm = RecordingLlmClient()
    llm.recordByUser("Garbage", "[]")

    val props = ProcessingProperties(monitoredCategories = listOf(Category.SOFTWARE_BACKEND))
    ClassifyWorker(queue, postings, txManager, llm, prompt, mapper, props).runOnce()

    assertEquals(QueueState.IRRELEVANT, queue.findById(queueRow.id!!).get().state)
  }
}
