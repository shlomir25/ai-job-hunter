package com.jobhunter.ingestion.scraper

import org.springframework.stereotype.Component

@Component
class HttpScraperRegistry(private val scrapers: List<HttpScraper>) {
  fun byCode(code: String): HttpScraper? = scrapers.firstOrNull { it.sourceCode == code }
}
