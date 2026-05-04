package com.jobhunter.matching.worker

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.jobhunter.core.AbstractRepositoryTest
import com.jobhunter.core.client.LlmClient
import com.jobhunter.core.domain.Cv
import com.jobhunter.core.domain.JobPosting
import com.jobhunter.core.domain.JobSource
import com.jobhunter.core.domain.MatchState
import com.jobhunter.core.domain.PostingEmbedding
import com.jobhunter.core.domain.ProcessingQueueRow
import com.jobhunter.core.domain.QueueState
import com.jobhunter.core.domain.SourceType
import com.jobhunter.core.repository.CvRepository
import com.jobhunter.core.repository.JobPostingRepository
import com.jobhunter.core.repository.JobSourceRepository
import com.jobhunter.core.repository.MatchRepository
import com.jobhunter.core.repository.PostingEmbeddingRepository
import com.jobhunter.core.repository.ProcessingQueueRepository
import com.jobhunter.matching.config.MatchingProperties
import com.jobhunter.matching.prompt.MatchPromptBuilder
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import java.time.Instant
import kotlin.test.assertEquals

class MatchWorkerTest : AbstractRepositoryTest() {

  @Autowired lateinit var sources: JobSourceRepository

  @Autowired lateinit var postings: JobPostingRepository

  @Autowired lateinit var queue: ProcessingQueueRepository

  @Autowired lateinit var embeddings: PostingEmbeddingRepository

  @Autowired lateinit var cvs: CvRepository

  @Autowired lateinit var matches: MatchRepository

  @Autowired lateinit var txManager: PlatformTransactionManager

  private val mapper = ObjectMapper().registerKotlinModule()
  private val promptBuilder = MatchPromptBuilder()

  private fun seedPosting(): Pair<JobPosting, ProcessingQueueRow> {
    val src = sources.save(JobSource("S-${System.nanoTime()}", SourceType.IMAP, true, "{}"))
    val p = postings.save(
      JobPosting(
        sourceId = src.id!!,
        externalId = "E-${System.nanoTime()}",
        rawText = "x",
        title = "Backend",
        capturedAt = Instant.now(),
      ),
    )
    embeddings.save(PostingEmbedding(p.id!!, FloatArray(1024) { 0.5f }, "bge-m3"))
    val r = queue.save(ProcessingQueueRow(jobPostingId = p.id!!, state = QueueState.EMBEDDED))
    return p to r
  }

  private fun seedActiveCv(embedding: FloatArray = FloatArray(1024) { 0.5f }) {
    cvs.save(
      Cv(
        "default",
        "cv.pdf",
        "application/pdf",
        byteArrayOf(0),
        "CV text",
        embedding,
        structuredSummary =
          """{"skills":[],"yearsTotalExperience":null,"languages":[],"pastRoles":[],"education":null,"highlights":null}""",
        isActive = true,
      ),
    )
  }

  private fun stubLlm(scoreJson: String): LlmClient {
    val llm = mockk<LlmClient>()
    every { llm.chatStructured(any(), any()) } returns scoreJson
    every { llm.chat(any(), any()) } returns scoreJson
    return llm
  }

  @Test
  fun `low cosine routes to IRRELEVANT, no match row`() {
    seedActiveCv(embedding = FloatArray(1024) { -0.5f })
    val (_, queueRow) = seedPosting()

    val llm = mockk<LlmClient>()
    val props = MatchingProperties(cosineThreshold = 0.95, llmScoreThreshold = 60)
    val worker =
      MatchWorker(queue, postings, embeddings, cvs, matches, txManager, llm, promptBuilder, mapper, props)
    worker.runOnce()

    assertEquals(QueueState.IRRELEVANT, queue.findById(queueRow.id!!).get().state)
    assertEquals(0L, matches.count())
  }

  @Test
  fun `high cosine and high llm score creates Match in READY_FOR_REVIEW`() {
    seedActiveCv()
    val (_, queueRow) = seedPosting()

    val llm = stubLlm("""{"score":85,"strengths":["Kotlin"],"gaps":[],"summary":"good fit"}""")
    val props = MatchingProperties(cosineThreshold = 0.0, llmScoreThreshold = 60)
    val worker =
      MatchWorker(queue, postings, embeddings, cvs, matches, txManager, llm, promptBuilder, mapper, props)
    worker.runOnce()

    assertEquals(QueueState.MATCHED, queue.findById(queueRow.id!!).get().state)
    assertEquals(1L, matches.count())
    val m = matches.findAll().first()
    assertEquals(MatchState.READY_FOR_REVIEW, m.state)
    assertEquals(85, m.llmScore)
  }

  @Test
  fun `high cosine but low llm score routes to IRRELEVANT`() {
    seedActiveCv()
    val (_, queueRow) = seedPosting()

    val llm = stubLlm("""{"score":30,"strengths":[],"gaps":["years"],"summary":"weak"}""")
    val props = MatchingProperties(cosineThreshold = 0.0, llmScoreThreshold = 60)
    MatchWorker(queue, postings, embeddings, cvs, matches, txManager, llm, promptBuilder, mapper, props).runOnce()

    assertEquals(QueueState.IRRELEVANT, queue.findById(queueRow.id!!).get().state)
    assertEquals(0L, matches.count())
  }
}
