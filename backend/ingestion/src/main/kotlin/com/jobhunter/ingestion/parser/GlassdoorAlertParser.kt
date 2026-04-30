package com.jobhunter.ingestion.parser

import com.jobhunter.ingestion.dto.ParsedJobAlertEmail
import com.jobhunter.ingestion.dto.ParsedPostingDraft
import com.jobhunter.ingestion.dto.RawEmail
import org.jsoup.Jsoup
import org.springframework.stereotype.Component

@Component
class GlassdoorAlertParser : EmailParser {
    override val sourceCode = "IMAP_GLASSDOOR_ALERTS"

    override fun supports(senderAddress: String): Boolean =
        senderAddress.lowercase().contains("@glassdoor.com")

    override fun parse(email: RawEmail): ParsedJobAlertEmail {
        val html = email.htmlBody ?: return ParsedJobAlertEmail(email, emptyList())
        val doc = Jsoup.parse(html)
        val anchors = doc.select("a[data-jobid]")
        val postings = anchors.mapNotNull { link ->
            val id = link.attr("data-jobid").ifBlank { return@mapNotNull null }
            val href = link.attr("href").ifBlank { null }
            val container = link.parent()
            ParsedPostingDraft(
                externalId = id,
                sourceUrl = href,
                rawText = container?.text() ?: link.text(),
                rawHtml = container?.outerHtml() ?: link.outerHtml(),
                title = link.text().ifBlank { null },
                company = container?.selectFirst("[data-test=employer-name]")?.text()?.ifBlank { null },
                location = container?.selectFirst("[data-test=job-location]")?.text()?.ifBlank { null },
                postedAt = email.receivedAt,
            )
        }
        return ParsedJobAlertEmail(email, postings)
    }
}
