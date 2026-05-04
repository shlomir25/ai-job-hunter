package com.jobhunter.ingestion.scraper

import org.junit.jupiter.api.Test
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AllJobsScraperTest {

  private val scraper = object : AllJobsScraper() {
    override fun fetchPages(config: Map<String, String>): List<Document> = listOf(
      Jsoup.parse(
        javaClass.getResourceAsStream("/scrapers/alljobs/sample-1.html")!!
          .bufferedReader().readText(),
      ),
    )
  }

  @Test
  fun `parses two postings from fixture`() {
    val results = scraper.scrape(emptyMap())
    assertEquals(2, results.size)
    assertEquals("Senior Backend Engineer", results[0].title)
    assertEquals("Acme Robotics", results[0].company)
    assertEquals("123", results[0].externalId)
  }

  @Test
  fun `sentinel check fails when first posting missing company`() {
    val brokenScraper = object : AllJobsScraper() {
      override fun fetchPages(config: Map<String, String>): List<Document> = listOf(
        Jsoup.parse(
          """<div class="job-content-top">
              <a class="job-content-top-title" href="/job/x">title</a>
            </div>""",
        ),
      )
    }
    assertFailsWith<IllegalArgumentException> {
      brokenScraper.scrape(emptyMap())
    }
  }
}
