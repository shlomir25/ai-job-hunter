package com.jobhunter.matching.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("jobhunter.matching")
data class MatchingProperties(
  val cosineThreshold: Double = 0.40,
  val llmScoreThreshold: Int = 60,
)
