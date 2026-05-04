package com.jobhunter.ingestion.scraper

import com.jobhunter.ingestion.dto.ParsedPostingDraft
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

abstract class BaseHttpScraper : HttpScraper {

  abstract fun fetchPages(config: Map<String, String>): List<Document>

  abstract fun parsePage(doc: Document): List<ParsedPostingDraft>

  override fun scrape(config: Map<String, String>): List<ParsedPostingDraft> {
    val all = fetchPages(config).flatMap { parsePage(it) }
    if (all.isNotEmpty()) {
      val first = all.first()
      require(!first.title.isNullOrBlank() && !first.company.isNullOrBlank()) {
        "Scraper $sourceCode produced posting with null title or company; selectors likely broken"
      }
    }
    return all
  }

  protected fun fetchHtml(url: String, userAgent: String = DEFAULT_UA): Document =
    Jsoup.connect(url)
      .userAgent(userAgent)
      .timeout(15_000)
      .get()

  companion object {
    const val DEFAULT_UA =
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15"
  }
}
