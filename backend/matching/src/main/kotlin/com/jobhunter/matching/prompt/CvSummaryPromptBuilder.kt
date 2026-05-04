package com.jobhunter.matching.prompt

import com.fasterxml.jackson.databind.ObjectMapper
import com.jobhunter.core.client.LlmClient
import com.jobhunter.matching.dto.CvSummary
import org.springframework.stereotype.Component

@Component
class CvSummaryPromptBuilder {

  fun systemPrompt(): String =
    """
    You extract a structured summary from a CV. Reply with one JSON object only:
    {
      "skills": [string],
      "yearsTotalExperience": int|null,
      "languages": [string],
      "pastRoles": [string],
      "education": string|null,
      "highlights": string|null
    }
    Use null or [] for missing fields. Reply with JSON only.
    """.trimIndent()

  fun invoke(llm: LlmClient, cvText: String, mapper: ObjectMapper): CvSummary {
    val response = llm.chatStructured(system = systemPrompt(), user = cvText)
    return parseOrRetry(llm, cvText, response, mapper)
  }

  private fun parseOrRetry(
    llm: LlmClient,
    cvText: String,
    firstResponse: String,
    mapper: ObjectMapper,
  ): CvSummary =
    try {
      mapper.readValue(firstResponse, CvSummary::class.java)
    } catch (_: Exception) {
      val retry = llm.chatStructured(
        system = systemPrompt() + "\n\nIMPORTANT: previous response was not valid JSON. Reply with valid JSON only.",
        user = cvText,
      )
      mapper.readValue(retry, CvSummary::class.java)
    }
}
