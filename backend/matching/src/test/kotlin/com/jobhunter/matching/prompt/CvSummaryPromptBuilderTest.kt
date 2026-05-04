package com.jobhunter.matching.prompt

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.jobhunter.matching.client.RecordingLlmClient
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CvSummaryPromptBuilderTest {

  private val mapper = ObjectMapper().registerKotlinModule()
  private val builder = CvSummaryPromptBuilder()

  private fun load(name: String): String =
    javaClass.getResourceAsStream("/prompts/cv-summary/$name")!!
      .bufferedReader().readText()

  @Test
  fun `parses CV summary from sample-1`() {
    val input = load("sample-1-input.txt")
    val expectedJson = load("sample-1-expected.json")

    val llm = RecordingLlmClient()
    llm.record(builder.systemPrompt(), input, expectedJson)

    val result = builder.invoke(llm, input, mapper)
    assertEquals(7, result.yearsTotalExperience)
    assertTrue(result.skills.contains("Kotlin"))
    assertTrue(result.languages.contains("Hebrew"))
  }
}
