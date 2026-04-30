package com.jobhunter.ingestion.parser

import com.jobhunter.ingestion.dto.ParsedJobAlertEmail
import com.jobhunter.ingestion.dto.ParsedPostingDraft
import com.jobhunter.ingestion.dto.RawEmail
import org.jsoup.Jsoup
import org.springframework.stereotype.Component

@Component
class LinkedInAlertParser : EmailParser {
  override val sourceCode = "IMAP_LINKEDIN_ALERTS"

  override fun supports(senderAddress: String): Boolean =
    senderAddress.lowercase().contains("@linkedin.com")

  override fun parse(email: RawEmail): ParsedJobAlertEmail {
    val html = email.htmlBody ?: return ParsedJobAlertEmail(email, emptyList())
    val doc = Jsoup.parse(html)
    val cards = doc.select("tr.job-card")
    val postings = cards.mapNotNull { card ->
      val link = card.selectFirst("a") ?: return@mapNotNull null
      val href = link.attr("href").ifBlank { return@mapNotNull null }
      val externalId = JOB_ID_PATTERN.find(href)?.groupValues?.get(1) ?: href.hashCode().toString()
      ParsedPostingDraft(
        externalId = externalId,
        sourceUrl = href,
        rawText = card.text(),
        rawHtml = card.outerHtml(),
        title = link.text().ifBlank { null },
        company = card.selectFirst(".company")?.text()?.ifBlank { null },
        location = card.selectFirst(".location")?.text()?.ifBlank { null },
        postedAt = email.receivedAt,
      )
    }
    return ParsedJobAlertEmail(email, postings)
  }

  companion object {
    private val JOB_ID_PATTERN = Regex("/jobs/view/(\\d+)/?")
  }
}
