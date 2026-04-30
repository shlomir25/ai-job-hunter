package com.jobhunter.processing.worker

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.jobhunter.core.AbstractRepositoryTest
import com.jobhunter.core.domain.JobPosting
import com.jobhunter.core.domain.JobSource
import com.jobhunter.core.domain.ProcessingQueueRow
import com.jobhunter.core.domain.QueueState
import com.jobhunter.core.domain.SourceType
import com.jobhunter.core.repository.JobPostingRepository
import com.jobhunter.core.repository.JobSourceRepository
import com.jobhunter.core.repository.ProcessingQueueRepository
import com.jobhunter.processing.client.RecordingLlmClient
import com.jobhunter.processing.prompt.ParsePromptBuilder
import com.jobhunter.processing.service.EmailExtractor
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.ContextConfiguration
import org.springframework.transaction.PlatformTransactionManager
import java.time.Instant
import kotlin.test.assertEquals

@ContextConfiguration(classes = [ParseWorkerTest.TestBeans::class])
class ParseWorkerTest : AbstractRepositoryTest() {

  @TestConfiguration
  class TestBeans {
    @Bean fun mapper() = ObjectMapper().registerKotlinModule()

    @Bean fun parsePromptBuilder() = ParsePromptBuilder()
  }

  @Autowired lateinit var sources: JobSourceRepository

  @Autowired lateinit var postings: JobPostingRepository

  @Autowired lateinit var queue: ProcessingQueueRepository

  @Autowired lateinit var txManager: PlatformTransactionManager

  @Autowired lateinit var mapper: ObjectMapper

  @Autowired lateinit var promptBuilder: ParsePromptBuilder

  @Test
  fun `parses INGESTED row, fills fields, advances to PARSED`() {
    val src = sources.save(JobSource("S", SourceType.IMAP, true, "{}"))
    val post = postings.save(
      JobPosting(
        sourceId = src.id!!,
        externalId = "E1",
        rawText = "Backend Engineer at Acme. Tel Aviv. Apply: jobs@acme.com",
        capturedAt = Instant.now(),
      ),
    )
    val queueRow = queue.save(ProcessingQueueRow(jobPostingId = post.id!!, state = QueueState.INGESTED))

    val llm = RecordingLlmClient()
    llm.recordByUser(
      user = post.rawText,
      response = """
                {"title":"Backend Engineer","company":"Acme","location":"Tel Aviv",
                 "isRemote":false,"language":"en","description":null,"requirements":null,
                 "salaryText":null,"applyUrl":null,"contactEmail":"jobs@acme.com"}
      """.trimIndent(),
    )
    val emailExtractor = EmailExtractor(llm)

    val worker = ParseWorker(
      queue = queue,
      postings = postings,
      txManager = txManager,
      llm = llm,
      promptBuilder = promptBuilder,
      emailExtractor = emailExtractor,
      mapper = mapper,
    )
    worker.runOnce()

    val updatedQueue = queue.findById(queueRow.id!!).get()
    assertEquals(QueueState.PARSED, updatedQueue.state)

    val updatedPosting = postings.findById(post.id!!).get()
    assertEquals("Backend Engineer", updatedPosting.title)
    assertEquals("Acme", updatedPosting.company)
    assertEquals("jobs@acme.com", updatedPosting.contactEmail)
    assertEquals("en", updatedPosting.language)
  }
}
