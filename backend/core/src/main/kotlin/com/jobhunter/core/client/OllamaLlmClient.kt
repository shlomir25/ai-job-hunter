package com.jobhunter.core.client

import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.stereotype.Component
import java.util.concurrent.Semaphore

@Component
class OllamaLlmClient(
    private val chatModel: ChatModel,
) : LlmClient {

    /**
     * The 32B model serializes naturally on a single Mac; we cap concurrency
     * at 1 to avoid OOM and contention.
     */
    private val semaphore = Semaphore(1)

    override fun chat(system: String, user: String): String {
        semaphore.acquire()
        try {
            val prompt = Prompt(listOf(SystemMessage(system), UserMessage(user)))
            return chatModel.call(prompt).result.output.text ?: ""
        } finally {
            semaphore.release()
        }
    }

    override fun chatStructured(system: String, user: String): String =
        stripFences(chat(system, user))

    private fun stripFences(raw: String): String {
        val trimmed = raw.trim()
        val fenced = Regex("^```(?:json)?\\s*(.*?)\\s*```$", RegexOption.DOT_MATCHES_ALL)
        return fenced.matchEntire(trimmed)?.groupValues?.get(1)?.trim() ?: trimmed
    }
}
