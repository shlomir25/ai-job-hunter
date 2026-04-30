package com.jobhunter.processing.prompt

import com.fasterxml.jackson.databind.ObjectMapper
import com.jobhunter.core.client.LlmClient
import com.jobhunter.core.domain.Category
import com.jobhunter.processing.dto.ClassificationResult
import org.springframework.stereotype.Component

@Component
class ClassifyPromptBuilder {

  fun systemPrompt(): String {
    val labels = Category.entries.joinToString(", ") { it.name }
    return """
            Classify this job posting. Pick zero or more from this exact list of labels:
            [$labels]
            Reply with a JSON array of labels, e.g. ["SOFTWARE_BACKEND","DEVOPS"].
            If none apply, reply with [].
            JSON only. No prose, no fences.
    """.trimIndent()
  }

  fun invoke(llm: LlmClient, rawText: String, mapper: ObjectMapper): ClassificationResult {
    val response = llm.chatStructured(system = systemPrompt(), user = rawText)
    val parsed: List<String> = parseOrRetry(llm, rawText, response, mapper)
    val cats = parsed.mapNotNull { name ->
      runCatching { Category.valueOf(name) }.getOrNull()
    }
    return ClassificationResult(cats)
  }

  @Suppress("UNCHECKED_CAST")
  private fun parseOrRetry(
    llm: LlmClient,
    rawText: String,
    firstResponse: String,
    mapper: ObjectMapper,
  ): List<String> = try {
    mapper.readValue(firstResponse, List::class.java) as List<String>
  } catch (_: Exception) {
    val retry = llm.chatStructured(
      system = systemPrompt() + "\n\nIMPORTANT: Your previous response was not valid JSON. Reply with a JSON array only.",
      user = rawText,
    )
    mapper.readValue(retry, List::class.java) as List<String>
  }
}
