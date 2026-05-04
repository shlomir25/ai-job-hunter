package com.jobhunter.matching.prompt

import com.fasterxml.jackson.databind.ObjectMapper
import com.jobhunter.core.client.LlmClient
import com.jobhunter.core.domain.JobPosting
import com.jobhunter.matching.dto.CvSummary
import com.jobhunter.matching.dto.MatchScoreResult
import org.springframework.stereotype.Component

@Component
class MatchPromptBuilder {

  fun systemPrompt(): String =
    """
    You evaluate fit between a candidate and a job. Output JSON:
    {"score": int 0-100, "strengths": [string], "gaps": [string], "summary": string}
    Score >= 60 means a strong-enough fit to surface to the candidate.
    Reply with JSON only.
    """.trimIndent()

  fun userPrompt(cvSummaryJson: String, posting: JobPosting): String =
    buildString {
      append("CV summary: ").append(cvSummaryJson).append("\n\n")
      append("Job:\n")
      append("Title: ").append(posting.title ?: "(unknown)").append("\n")
      append("Requirements: ").append(posting.requirements ?: "(none specified)").append("\n")
      append("Description: ").append(posting.description ?: "(none specified)").append("\n")
    }

  fun invoke(
    llm: LlmClient,
    cvSummary: CvSummary,
    posting: JobPosting,
    mapper: ObjectMapper,
  ): MatchScoreResult {
    val cvJson = mapper.writeValueAsString(cvSummary)
    return invokeFromUser(llm, userPrompt(cvJson, posting), mapper)
  }

  fun invokeFromUser(llm: LlmClient, user: String, mapper: ObjectMapper): MatchScoreResult {
    val response = llm.chatStructured(system = systemPrompt(), user = user)
    return parseOrRetry(llm, user, response, mapper)
  }

  private fun parseOrRetry(
    llm: LlmClient,
    user: String,
    firstResponse: String,
    mapper: ObjectMapper,
  ): MatchScoreResult =
    try {
      mapper.readValue(firstResponse, MatchScoreResult::class.java)
    } catch (_: Exception) {
      val retry = llm.chatStructured(
        system = systemPrompt() + "\n\nIMPORTANT: previous response was not valid JSON. Reply with valid JSON only.",
        user = user,
      )
      mapper.readValue(retry, MatchScoreResult::class.java)
    }
}
