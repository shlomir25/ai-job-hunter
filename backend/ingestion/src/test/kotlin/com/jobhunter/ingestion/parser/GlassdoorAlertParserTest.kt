package com.jobhunter.ingestion.parser

import com.jobhunter.ingestion.dto.RawEmail
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class GlassdoorAlertParserTest {
  private val parser = GlassdoorAlertParser()

  private fun loadFixture(): String =
    javaClass.getResourceAsStream("/email-fixtures/glassdoor/sample-1.html")!!
      .bufferedReader().readText()

  @Test
  fun `supports glassdoor alert sender`() {
    assertEquals(true, parser.supports("noreply@glassdoor.com"))
    assertEquals(false, parser.supports("foo@example.com"))
  }

  @Test
  fun `extracts postings using data-jobid`() {
    val email = RawEmail(
      messageId = "<m@glassdoor.com>",
      from = "noreply@glassdoor.com",
      subject = "Daily jobs",
      receivedAt = Instant.now(),
      htmlBody = loadFixture(),
      textBody = null,
    )
    val parsed = parser.parse(email)
    assertEquals(1, parsed.postings.size)
    val p = parsed.postings[0]
    assertEquals("Senior SRE", p.title)
    assertEquals("Cloud Co", p.company)
    assertEquals("Israel", p.location)
    assertEquals("987654", p.externalId)
  }
}
