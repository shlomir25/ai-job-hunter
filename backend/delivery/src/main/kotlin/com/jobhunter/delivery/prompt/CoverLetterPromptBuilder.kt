package com.jobhunter.delivery.prompt

import com.jobhunter.core.client.LlmClient
import com.jobhunter.core.domain.JobPosting
import com.jobhunter.delivery.dto.DraftedEmail
import org.springframework.stereotype.Component

@Component
class CoverLetterPromptBuilder {

  fun systemPrompt(language: String): String =
    when (language.lowercase()) {
      "he" ->
        """
        אתה כותב מכתב מקדים מקצועי בעברית, באורך של 4-6 משפטים.
        הזכר 2-3 התאמות ספציפיות בכישורים. טקסט רגיל בלבד, ללא נושא, ללא מצייני placeholder.
        """.trimIndent()
      else ->
        """
        You write a professional cover letter in English, 4-6 sentences.
        Mention 2-3 specific skill matches. Plain text only, no greeting placeholders, no subject line.
        """.trimIndent()
    }

  fun userPrompt(
    candidateName: String,
    cvSummaryJson: String,
    posting: JobPosting,
    strengths: List<String>,
  ): String =
    buildString {
      append("CV summary: ").append(cvSummaryJson).append("\n\n")
      append("Job:\n")
      append("Title: ").append(posting.title ?: "").append("\n")
      append("Company: ").append(posting.company ?: "").append("\n")
      append("Requirements: ").append(posting.requirements ?: "").append("\n\n")
      append("Top match strengths: ").append(strengths.joinToString(", ")).append("\n")
      append("Candidate name: ").append(candidateName).append("\n")
    }

  fun invoke(
    llm: LlmClient,
    candidateName: String,
    cvSummaryJson: String,
    posting: JobPosting,
    strengths: List<String>,
  ): DraftedEmail {
    val sys = systemPrompt(posting.language ?: "en")
    val user = userPrompt(candidateName, cvSummaryJson, posting, strengths)
    val body = llm.chat(sys, user).trim()
    val subject = subject(posting, candidateName)
    return DraftedEmail(subject = subject, body = body)
  }

  private fun subject(posting: JobPosting, candidateName: String): String {
    val title = posting.title ?: "Open role"
    return when ((posting.language ?: "en").lowercase()) {
      "he" -> "מועמדות לתפקיד $title — $candidateName"
      else -> "Application for $title — $candidateName"
    }
  }
}
