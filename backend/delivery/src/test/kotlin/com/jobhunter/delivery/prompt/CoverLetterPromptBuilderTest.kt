package com.jobhunter.delivery.prompt

import com.jobhunter.core.domain.JobPosting
import com.jobhunter.delivery.client.RecordingLlmClient
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoverLetterPromptBuilderTest {

  private val builder = CoverLetterPromptBuilder()

  private fun load(name: String): String =
    javaClass.getResourceAsStream("/prompts/cover-letter/$name")!!
      .bufferedReader().readText()

  private fun makePosting(language: String): JobPosting =
    JobPosting(
      sourceId = 1,
      externalId = "x",
      rawText = "raw",
      title = "Backend Engineer (Kotlin)",
      company = "Acme",
      requirements = "5+ years backend",
      language = language,
      capturedAt = Instant.now(),
    )

  @Test
  fun `english system prompt is in english`() {
    val sys = builder.systemPrompt("en")
    assertTrue(sys.contains("English"))
    assertFalse(sys.contains("Hebrew"))
  }

  @Test
  fun `hebrew system prompt is in hebrew`() {
    val sys = builder.systemPrompt("he")
    assertTrue(sys.contains("בעברית"))
  }

  @Test
  fun `english draft from recorded LLM`() {
    val expected = load("en/sample-1-expected.txt")
    val llm = RecordingLlmClient()
    val posting = makePosting("en")

    val cvSummaryJson = """{"skills":["Kotlin","Spring Boot","Postgres"],"yearsTotalExperience":7}"""
    val matchStrengths = listOf("Kotlin", "Postgres")
    val candidateName = "Shlomi Rahimi"

    val userPrompt = builder.userPrompt(candidateName, cvSummaryJson, posting, matchStrengths)
    llm.record(builder.systemPrompt("en"), userPrompt, expected.trim())

    val drafted = builder.invoke(llm, candidateName, cvSummaryJson, posting, matchStrengths)
    assertTrue(drafted.body.contains("Acme"))
    assertTrue(drafted.subject.contains("Backend Engineer"))
    assertTrue(drafted.subject.contains("Shlomi Rahimi"))
  }
}
