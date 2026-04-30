package com.jobhunter.processing.service

import com.jobhunter.core.client.LlmClient
import org.springframework.stereotype.Component

@Component
class EmailExtractor(private val llm: LlmClient) {

  fun extract(rawText: String, companyHint: String?): String? {
    val candidates = EMAIL_REGEX.findAll(rawText).map { it.value }.toList()
    if (candidates.isNotEmpty()) {
      return pickBest(candidates, companyHint)
    }
    // Fallback: ask the LLM, but VALIDATE the response against the same regex.
    val response = try {
      llm.chat(
        system = "You extract a contact email from text. Reply with ONLY the email address or the literal word null. No prose.",
        user = rawText,
      ).trim()
    } catch (_: Exception) {
      return null
    }
    if (response.equals("null", ignoreCase = true)) return null
    // Re-validate. This is the hallucination guard.
    val match = EMAIL_REGEX.find(response) ?: return null
    return match.value
  }

  private fun pickBest(candidates: List<String>, companyHint: String?): String? {
    val nonNoreply = candidates.filterNot { isNoreply(it) }
    val pool = nonNoreply.ifEmpty { candidates }
    if (companyHint != null) {
      val token = companyHint.lowercase().filter { it.isLetterOrDigit() }
      val matching = pool.firstOrNull { token.isNotEmpty() && it.lowercase().contains(token) }
      if (matching != null) return matching
    }
    return pool.firstOrNull()
  }

  private fun isNoreply(email: String): Boolean {
    val local = email.substringBefore('@').lowercase()
    return local.startsWith("noreply") || local.startsWith("no-reply") ||
      local.startsWith("donotreply") || local.startsWith("do-not-reply")
  }

  companion object {
    // RFC 5322-lite — sufficient for posting bodies, conservative enough to reject garbage.
    private val EMAIL_REGEX = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
  }
}
