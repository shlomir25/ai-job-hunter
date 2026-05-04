package com.jobhunter.matching.service

import com.jobhunter.core.domain.Match
import com.jobhunter.core.repository.JobPostingRepository
import com.jobhunter.core.repository.MatchRepository
import com.jobhunter.matching.dto.MatchView
import com.jobhunter.matching.dto.PostingView
import org.springframework.stereotype.Service

@Service
class MatchService(
  private val matches: MatchRepository,
  private val postings: JobPostingRepository,
) {

  fun listReady(): List<MatchView> = matches.findReadyForReview().mapNotNull { toView(it) }

  fun get(id: Long): MatchView? = matches.findById(id).map { toView(it) }.orElse(null)

  private fun toView(m: Match): MatchView? {
    val p = postings.findById(m.jobPostingId).orElse(null) ?: return null
    return MatchView(
      id = m.id!!,
      state = m.state,
      llmScore = m.llmScore,
      cosineSimilarity = m.cosineSimilarity,
      reasoning = m.llmReasoning,
      posting = PostingView(
        id = p.id!!,
        title = p.title,
        company = p.company,
        location = p.location,
        isRemote = p.isRemote,
        language = p.language,
        description = p.description,
        requirements = p.requirements,
        contactEmail = p.contactEmail,
        applyUrl = p.applyUrl,
        sourceUrl = p.sourceUrl,
      ),
      createdAt = m.createdAt,
    )
  }
}
