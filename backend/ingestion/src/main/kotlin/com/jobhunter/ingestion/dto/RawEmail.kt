package com.jobhunter.ingestion.dto

import java.time.Instant

data class RawEmail(
  val messageId: String,
  val from: String,
  val subject: String,
  val receivedAt: Instant,
  val htmlBody: String?,
  val textBody: String?,
)
