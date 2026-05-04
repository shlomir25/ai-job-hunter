package com.jobhunter.ingestion.scraper

import com.jobhunter.ingestion.dto.ParsedPostingDraft
import org.jsoup.nodes.Document
import org.springframework.stereotype.Component
import java.time.Instant

@Component
open class AllJobsScraper : BaseHttpScraper() {
  override val sourceCode = "SCRAPER_ALLJOBS"

  override fun fetchPages(config: Map<String, String>): List<Document> {
    val baseUrl = config["url"] ?: "https://www.alljobs.co.il/SearchResultsGuest.aspx"
    return listOf(fetchHtml(baseUrl))
  }

  override fun parsePage(doc: Document): List<ParsedPostingDraft> {
    val cards = doc.select("div.job-content-top")
    return cards.mapNotNull { card ->
      val link = card.selectFirst("a.job-content-top-title") ?: return@mapNotNull null
      val href = link.attr("href").ifBlank { return@mapNotNull null }
      val id = ID_PATTERN.find(href)?.groupValues?.get(1) ?: href.hashCode().toString()
      ParsedPostingDraft(
        externalId = id,
        sourceUrl = href.let { if (it.startsWith("http")) it else "https://www.alljobs.co.il$it" },
        rawText = card.text(),
        rawHtml = card.outerHtml(),
        title = link.text().ifBlank { null },
        company = card.selectFirst(".job-content-top-employer")?.text()?.ifBlank { null },
        location = card.selectFirst(".job-content-top-location")?.text()?.ifBlank { null },
        postedAt = Instant.now(),
      )
    }
  }

  companion object {
    private val ID_PATTERN = Regex("/job/(\\d+)")
  }
}
