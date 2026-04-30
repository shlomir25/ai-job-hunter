package com.jobhunter.processing.prompt

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.jobhunter.core.domain.Category
import com.jobhunter.processing.client.RecordingLlmClient
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ClassifyPromptBuilderTest {

  private val mapper = ObjectMapper().registerKotlinModule()
  private val builder = ClassifyPromptBuilder()

  private fun load(name: String): String =
    javaClass.getResourceAsStream("/prompts/classify/$name")!!
      .bufferedReader().readText()

  @Test
  fun `parses sample-1 categories`() {
    val input = load("sample-1-input.txt")
    val expectedJson = load("sample-1-expected.json")

    val llm = RecordingLlmClient()
    llm.record(builder.systemPrompt(), input, expectedJson)

    val result = builder.invoke(llm, input, mapper)
    assertEquals(listOf(Category.SOFTWARE_BACKEND), result.categories)
  }

  @Test
  fun `empty array means no labels`() {
    val llm = RecordingLlmClient()
    llm.recordByUser("foo", "[]")
    val result = builder.invoke(llm, "foo", mapper)
    assertEquals(emptyList(), result.categories)
  }
}
