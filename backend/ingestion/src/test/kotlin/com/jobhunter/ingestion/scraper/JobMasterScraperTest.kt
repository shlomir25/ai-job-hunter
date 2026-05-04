package com.jobhunter.ingestion.scraper

import org.junit.jupiter.api.Test
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import kotlin.test.assertEquals

class JobMasterScraperTest {

  private val scraper = object : JobMasterScraper() {
    override fun fetchPages(config: Map<String, String>): List<Document> = listOf(
      Jsoup.parse(
        javaClass.getResourceAsStream("/scrapers/jobmaster/sample-1.html")!!
          .bufferedReader().readText(),
      ),
    )
  }

  @Test
  fun `parses postings`() {
    val results = scraper.scrape(emptyMap())
    assertEquals(2, results.size)
    assertEquals("Senior Platform Engineer", results[0].title)
    assertEquals("Cloud Co", results[0].company)
    assertEquals("4242", results[0].externalId)
  }
}
