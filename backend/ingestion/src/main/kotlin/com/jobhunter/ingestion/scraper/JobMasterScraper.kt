package com.jobhunter.ingestion.scraper

import com.jobhunter.ingestion.dto.ParsedPostingDraft
import org.jsoup.nodes.Document
import org.springframework.stereotype.Component
import java.time.Instant

@Component
open class JobMasterScraper : BaseHttpScraper() {
  override val sourceCode = "SCRAPER_JOBMASTER"

  override fun fetchPages(config: Map<String, String>): List<Document> {
    val baseUrl = config["url"] ?: "https://www.jobmaster.co.il/jobs/"
    return listOf(fetchHtml(baseUrl))
  }

  override fun parsePage(doc: Document): List<ParsedPostingDraft> {
    val cards = doc.select("div.job_card")
    return cards.mapNotNull { card ->
      val link = card.selectFirst("a.job_title") ?: return@mapNotNull null
      val href = link.attr("href").ifBlank { return@mapNotNull null }
      val id = ID_PATTERN.find(href)?.groupValues?.get(1) ?: href.hashCode().toString()
      ParsedPostingDraft(
        externalId = id,
        sourceUrl = if (href.startsWith("http")) href else "https://www.jobmaster.co.il$href",
        rawText = card.text(),
        rawHtml = card.outerHtml(),
        title = link.text().ifBlank { null },
        company = card.selectFirst(".company_name")?.text()?.ifBlank { null },
        location = card.selectFirst(".location")?.text()?.ifBlank { null },
        postedAt = Instant.now(),
      )
    }
  }

  companion object {
    private val ID_PATTERN = Regex("[?&]id=(\\d+)")
  }
}
