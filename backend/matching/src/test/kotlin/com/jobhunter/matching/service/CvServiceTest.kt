package com.jobhunter.matching.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.jobhunter.core.AbstractRepositoryTest
import com.jobhunter.core.client.EmbeddingClient
import com.jobhunter.core.domain.Cv
import com.jobhunter.core.repository.CvRepository
import com.jobhunter.matching.client.RecordingLlmClient
import com.jobhunter.matching.prompt.CvSummaryPromptBuilder
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CvServiceTest : AbstractRepositoryTest() {

  @Autowired lateinit var cvs: CvRepository

  private val parseService = CvParseService()
  private val summaryBuilder = CvSummaryPromptBuilder()
  private val mapper = ObjectMapper().registerKotlinModule()

  @Test
  fun `uploads CV, sets active, deactivates previous active`() {
    cvs.save(
      Cv(
        "old",
        "old.pdf",
        "application/pdf",
        byteArrayOf(0),
        "old text",
        FloatArray(1024),
        null,
        isActive = true,
      ),
    )

    val embeddingClient = mockk<EmbeddingClient>()
    every { embeddingClient.embed(any()) } returns FloatArray(1024) { 0.5f }
    val llm = RecordingLlmClient()
    llm.record(
      summaryBuilder.systemPrompt(),
      "Sample CV text",
      """{"skills":["Kotlin"],"yearsTotalExperience":5,"languages":["English"],"pastRoles":[],"education":null,"highlights":null}""",
    )

    val service = CvService(cvs, parseService, summaryBuilder, llm, embeddingClient, mapper)

    val pdfBytes = javaClass.getResourceAsStream("/cv-fixtures/sample.pdf")!!.readBytes()
    val response = service.upload(
      label = "current",
      fileName = "cv.pdf",
      mimeType = "application/pdf",
      bytes = pdfBytes,
      overrideText = "Sample CV text",
    )

    assertNotNull(response.id)
    assertEquals("current", response.label)

    val active = cvs.findActive()
    assertNotNull(active)
    assertEquals("current", active.label)

    val all = cvs.findAll()
    assertEquals(2, all.size)
    assertEquals(1, all.count { it.isActive })
  }
}
