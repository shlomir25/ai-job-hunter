package com.jobhunter.ingestion.dto

data class ParsedJobAlertEmail(
  val sourceEmail: RawEmail,
  val postings: List<ParsedPostingDraft>,
)
