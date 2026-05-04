package com.jobhunter.delivery.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.jobhunter.core.client.LlmClient
import com.jobhunter.core.domain.MatchState
import com.jobhunter.core.repository.CvRepository
import com.jobhunter.core.repository.JobPostingRepository
import com.jobhunter.core.repository.MatchRepository
import com.jobhunter.delivery.config.DeliveryProperties
import com.jobhunter.delivery.dto.DraftedEmail
import com.jobhunter.delivery.prompt.CoverLetterPromptBuilder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class CoverLetterService(
  private val matches: MatchRepository,
  private val postings: JobPostingRepository,
  private val cvs: CvRepository,
  private val props: DeliveryProperties,
  private val promptBuilder: CoverLetterPromptBuilder,
  private val llm: LlmClient,
  private val mapper: ObjectMapper,
) {

  @Transactional
  fun draft(matchId: Long): DraftedEmail {
    val match = matches.findById(matchId).orElseThrow {
      IllegalArgumentException("Match $matchId not found")
    }
    require(match.state == MatchState.READY_FOR_REVIEW || match.state == MatchState.DRAFTED) {
      "Cannot draft a match in state ${match.state}"
    }
    val posting = postings.findById(match.jobPostingId).orElseThrow {
      IllegalStateException("Posting ${match.jobPostingId} not found")
    }
    val cv = cvs.findById(match.cvId).orElseThrow {
      IllegalStateException("CV ${match.cvId} not found")
    }
    val strengths = extractStrengths(match.llmReasoning)
    val drafted = promptBuilder.invoke(
      llm = llm,
      candidateName = props.candidateName,
      cvSummaryJson = cv.structuredSummary ?: "{}",
      posting = posting,
      strengths = strengths,
    )

    match.draftSubject = drafted.subject
    match.draftBody = drafted.body
    match.state = MatchState.DRAFTED
    match.updatedAt = Instant.now()
    matches.save(match)

    return drafted
  }

  private fun extractStrengths(reasoningJson: String?): List<String> {
    if (reasoningJson.isNullOrBlank()) return emptyList()
    return try {
      val node: JsonNode = mapper.readTree(reasoningJson)
      node.get("strengths")?.let { arr ->
        arr.elements().asSequence().mapNotNull { it.asText().takeIf { v -> v.isNotBlank() } }.toList()
      } ?: emptyList()
    } catch (_: Exception) {
      emptyList()
    }
  }
}
