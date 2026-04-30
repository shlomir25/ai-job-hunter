package com.jobhunter.ingestion.dto

import java.time.Instant

data class ParsedPostingDraft(
    val externalId: String,    // stable ID — typically a hash of source-url + title + company
    val sourceUrl: String?,
    val rawText: String,       // text content for downstream parsing
    val rawHtml: String?,
    val title: String?,        // pre-filled if email gives it cleanly; otherwise null
    val company: String?,
    val location: String?,
    val postedAt: Instant?,
)
