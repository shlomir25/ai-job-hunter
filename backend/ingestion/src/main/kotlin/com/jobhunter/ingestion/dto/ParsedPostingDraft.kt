package com.jobhunter.ingestion.dto

import java.time.Instant

data class ParsedPostingDraft(
  val externalId: String,
  val sourceUrl: String?,
  val rawText: String,
  val rawHtml: String?,
  val title: String?,
  val company: String?,
  val location: String?,
  val postedAt: Instant?,
)
