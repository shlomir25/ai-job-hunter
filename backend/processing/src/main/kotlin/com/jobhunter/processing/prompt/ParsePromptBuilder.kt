package com.jobhunter.processing.prompt

import com.fasterxml.jackson.databind.ObjectMapper
import com.jobhunter.core.client.LlmClient
import com.jobhunter.processing.dto.ParsedFields
import org.springframework.stereotype.Component

@Component
class ParsePromptBuilder {

  fun systemPrompt(): String = """
        You extract structured data from a single job posting. Reply with ONE JSON object only,
        matching this schema (use null for missing fields, do NOT invent values):
        {
          "title": string|null,
          "company": string|null,
          "location": string|null,
          "isRemote": boolean|null,
          "language": "he"|"en"|null,
          "description": string|null,
          "requirements": string|null,
          "salaryText": string|null,
          "applyUrl": string|null,
          "contactEmail": string|null
        }
        Reply with JSON only. No markdown fences, no prose.
  """.trimIndent()

  /**
   * Calls the LLM with the structured-output prompt, parses the JSON response,
   * and retries once with a stricter reminder if parsing fails.
   */
  fun invoke(llm: LlmClient, rawText: String, mapper: ObjectMapper): ParsedFields {
    val first = llm.chatStructured(system = systemPrompt(), user = rawText)
    return parseOrRetry(llm, rawText, first, mapper)
  }

  private fun parseOrRetry(
    llm: LlmClient,
    rawText: String,
    firstResponse: String,
    mapper: ObjectMapper,
  ): ParsedFields {
    try {
      return mapper.readValue(firstResponse, ParsedFields::class.java)
    } catch (_: Exception) {
      // strict retry
      val retry = llm.chatStructured(
        system = systemPrompt() + "\n\nIMPORTANT: Your previous response was not valid JSON. Reply with valid JSON only.",
        user = rawText,
      )
      return mapper.readValue(retry, ParsedFields::class.java)
    }
  }
}
