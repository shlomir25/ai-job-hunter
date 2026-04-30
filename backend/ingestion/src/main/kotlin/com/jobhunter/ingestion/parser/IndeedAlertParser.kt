package com.jobhunter.ingestion.parser

import com.jobhunter.ingestion.dto.ParsedJobAlertEmail
import com.jobhunter.ingestion.dto.ParsedPostingDraft
import com.jobhunter.ingestion.dto.RawEmail
import org.jsoup.Jsoup
import org.springframework.stereotype.Component

@Component
class IndeedAlertParser : EmailParser {
    override val sourceCode = "IMAP_INDEED_ALERTS"

    override fun supports(senderAddress: String): Boolean =
        senderAddress.lowercase().contains("@indeed.com")

    override fun parse(email: RawEmail): ParsedJobAlertEmail {
        val html = email.htmlBody ?: return ParsedJobAlertEmail(email, emptyList())
        val doc = Jsoup.parse(html)
        val results = doc.select("div.result")
        val postings = results.mapNotNull { card ->
            val link = card.selectFirst("a") ?: return@mapNotNull null
            val href = link.attr("href").ifBlank { return@mapNotNull null }
            val jk = JK_PATTERN.find(href)?.groupValues?.get(1) ?: href.hashCode().toString()
            ParsedPostingDraft(
                externalId = jk,
                sourceUrl = href,
                rawText = card.text(),
                rawHtml = card.outerHtml(),
                title = link.text().ifBlank { null },
                company = card.selectFirst(".company")?.text()?.ifBlank { null },
                location = card.selectFirst(".loc")?.text()?.ifBlank { null },
                postedAt = email.receivedAt,
            )
        }
        return ParsedJobAlertEmail(email, postings)
    }

    companion object {
        private val JK_PATTERN = Regex("[?&]jk=([a-zA-Z0-9]+)")
    }
}
