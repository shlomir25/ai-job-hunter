package com.jobhunter.matching.dto

import com.jobhunter.core.domain.MatchState
import java.time.Instant

data class MatchView(
  val id: Long,
  val state: MatchState,
  val llmScore: Int?,
  val cosineSimilarity: Double,
  val reasoning: String?,
  val posting: PostingView,
  val createdAt: Instant,
)

data class PostingView(
  val id: Long,
  val title: String?,
  val company: String?,
  val location: String?,
  val isRemote: Boolean?,
  val language: String?,
  val description: String?,
  val requirements: String?,
  val contactEmail: String?,
  val applyUrl: String?,
  val sourceUrl: String?,
)
