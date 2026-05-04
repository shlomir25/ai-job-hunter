package com.jobhunter.ingestion.scraper

import com.jobhunter.ingestion.dto.ParsedPostingDraft

interface HttpScraper {
  val sourceCode: String

  fun scrape(config: Map<String, String>): List<ParsedPostingDraft>
}
