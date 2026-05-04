package com.jobhunter.matching.prompt

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.jobhunter.matching.client.RecordingLlmClient
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MatchPromptBuilderTest {

  private val mapper = ObjectMapper().registerKotlinModule()
  private val builder = MatchPromptBuilder()

  private fun load(name: String): String =
    javaClass.getResourceAsStream("/prompts/match/$name")!!
      .bufferedReader().readText()

  @Test
  fun `parses match score from sample-1`() {
    val input = load("sample-1-input.json")
    val expectedJson = load("sample-1-expected.json")

    val llm = RecordingLlmClient()
    llm.record(builder.systemPrompt(), input, expectedJson)

    val result = builder.invokeFromUser(llm, input, mapper)
    assertEquals(85, result.score)
    assertTrue(result.strengths.contains("Kotlin"))
  }
}
