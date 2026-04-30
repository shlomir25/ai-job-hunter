package com.jobhunter.core.client

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.prompt.Prompt
import kotlin.test.assertEquals

class OllamaLlmClientTest {

    private fun chatModelReturning(text: String): ChatModel {
        val model = mockk<ChatModel>()
        val response = ChatResponse(listOf(Generation(AssistantMessage(text))))
        every { model.call(any<Prompt>()) } returns response
        return model
    }

    @Test
    fun `chat returns assistant text`() {
        val client = OllamaLlmClient(chatModelReturning("hello"))
        assertEquals("hello", client.chat(system = "be brief", user = "hi"))
    }

    @Test
    fun `chatStructured strips markdown fences and returns JSON`() {
        val client = OllamaLlmClient(chatModelReturning("```json\n{\"x\":1}\n```"))
        val json = client.chatStructured(system = "json only", user = "go")
        assertEquals("{\"x\":1}", json)
    }

    @Test
    fun `chat passes system and user messages to model`() {
        val model = chatModelReturning("ok")
        val client = OllamaLlmClient(model)
        client.chat("S", "U")
        verify {
            model.call(match<Prompt> { prompt ->
                val msgs = prompt.instructions
                msgs.size == 2 && msgs[0].text == "S" && msgs[1].text == "U"
            })
        }
    }
}
