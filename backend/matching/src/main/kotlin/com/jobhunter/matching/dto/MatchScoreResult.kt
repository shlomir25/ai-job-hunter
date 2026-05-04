package com.jobhunter.matching.dto

data class MatchScoreResult(
  val score: Int,
  val strengths: List<String>,
  val gaps: List<String>,
  val summary: String,
)
