package com.jobhunter.processing.prompt

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.jobhunter.processing.client.RecordingLlmClient
import com.jobhunter.processing.dto.ParsedFields
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ParsePromptBuilderTest {

    private val mapper = ObjectMapper().registerKotlinModule()
    private val builder = ParsePromptBuilder()

    private fun load(name: String): String =
        javaClass.getResourceAsStream("/prompts/parse/$name")!!
            .bufferedReader().readText()

    @Test
    fun `parses sample-1 against recorded response`() {
        val input = load("sample-1-input.txt")
        val expectedJson = load("sample-1-expected.json")

        val llm = RecordingLlmClient()
        llm.record(builder.systemPrompt(), input, expectedJson)

        val result: ParsedFields = builder.invoke(llm, input, mapper)
        val expected: ParsedFields = mapper.readValue(expectedJson, ParsedFields::class.java)
        assertEquals(expected.title, result.title)
        assertEquals(expected.company, result.company)
        assertEquals("en", result.language)
        assertEquals("jobs@acme-robotics.com", result.contactEmail)
    }
}
