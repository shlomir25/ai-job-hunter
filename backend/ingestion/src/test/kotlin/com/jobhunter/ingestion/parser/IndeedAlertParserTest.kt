package com.jobhunter.ingestion.parser

import com.jobhunter.ingestion.dto.RawEmail
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class IndeedAlertParserTest {
  private val parser = IndeedAlertParser()

  private fun loadFixture(): String =
    javaClass.getResourceAsStream("/email-fixtures/indeed/sample-1.html")!!
      .bufferedReader().readText()

  @Test
  fun `supports indeed alert sender`() {
    assertEquals(true, parser.supports("alerts@indeed.com"))
    assertEquals(false, parser.supports("foo@example.com"))
  }

  @Test
  fun `extracts postings`() {
    val email = RawEmail(
      messageId = "<m@indeed.com>",
      from = "alerts@indeed.com",
      subject = "Jobs",
      receivedAt = Instant.now(),
      htmlBody = loadFixture(),
      textBody = null,
    )
    val parsed = parser.parse(email)
    assertEquals(2, parsed.postings.size)
    assertEquals("Backend Engineer (Kotlin)", parsed.postings[0].title)
    assertEquals("Acme", parsed.postings[0].company)
    assertEquals("abc123", parsed.postings[0].externalId)
  }
}
