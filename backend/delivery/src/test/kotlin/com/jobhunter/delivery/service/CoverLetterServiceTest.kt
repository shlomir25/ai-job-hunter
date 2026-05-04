package com.jobhunter.delivery.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.jobhunter.core.AbstractRepositoryTest
import com.jobhunter.core.domain.Cv
import com.jobhunter.core.domain.JobPosting
import com.jobhunter.core.domain.JobSource
import com.jobhunter.core.domain.Match
import com.jobhunter.core.domain.MatchState
import com.jobhunter.core.domain.SourceType
import com.jobhunter.core.repository.CvRepository
import com.jobhunter.core.repository.JobPostingRepository
import com.jobhunter.core.repository.JobSourceRepository
import com.jobhunter.core.repository.MatchRepository
import com.jobhunter.delivery.client.RecordingLlmClient
import com.jobhunter.delivery.config.DeliveryProperties
import com.jobhunter.delivery.prompt.CoverLetterPromptBuilder
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CoverLetterServiceTest : AbstractRepositoryTest() {

  @Autowired lateinit var sources: JobSourceRepository

  @Autowired lateinit var postings: JobPostingRepository

  @Autowired lateinit var cvs: CvRepository

  @Autowired lateinit var matches: MatchRepository

  private val mapper = ObjectMapper().registerKotlinModule()
  private val promptBuilder = CoverLetterPromptBuilder()

  @Test
  fun `drafts cover letter and advances match to DRAFTED`() {
    val src = sources.save(JobSource("S", SourceType.IMAP, true, "{}"))
    val post = postings.save(
      JobPosting(
        sourceId = src.id!!,
        externalId = "E",
        rawText = "x",
        title = "Backend Engineer",
        company = "Acme",
        requirements = "Kotlin",
        language = "en",
        capturedAt = Instant.now(),
      ),
    )
    val cv = cvs.save(
      Cv(
        "default",
        "cv.pdf",
        "application/pdf",
        byteArrayOf(0),
        "CV text",
        FloatArray(1024),
        structuredSummary = """{"skills":["Kotlin"],"yearsTotalExperience":7}""",
        isActive = true,
      ),
    )
    val match = matches.save(
      Match(
        jobPostingId = post.id!!,
        cvId = cv.id!!,
        cosineSimilarity = 0.8,
        llmScore = 85,
        llmReasoning = """{"score":85,"strengths":["Kotlin"],"gaps":[],"summary":"good"}""",
        state = MatchState.READY_FOR_REVIEW,
      ),
    )

    val llm = RecordingLlmClient()
    val sys = promptBuilder.systemPrompt("en")
    val userArg = promptBuilder.userPrompt(
      candidateName = "Shlomi",
      cvSummaryJson = cv.structuredSummary!!,
      posting = post,
      strengths = listOf("Kotlin"),
    )
    llm.record(sys, userArg, "I am applying for the role.")

    val service = CoverLetterService(
      matches,
      postings,
      cvs,
      DeliveryProperties(candidateName = "Shlomi"),
      promptBuilder,
      llm,
      mapper,
    )

    val drafted = service.draft(match.id!!)
    assertNotNull(drafted)
    assertEquals("Application for Backend Engineer — Shlomi", drafted.subject)
    assertEquals("I am applying for the role.", drafted.body)

    val updated = matches.findById(match.id!!).get()
    assertEquals(MatchState.DRAFTED, updated.state)
    assertEquals("I am applying for the role.", updated.draftBody)
  }
}
