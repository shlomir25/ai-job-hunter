package com.jobhunter.ingestion.parser

import com.jobhunter.ingestion.dto.RawEmail
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class LinkedInAlertParserTest {

    private val parser = LinkedInAlertParser()

    private fun loadFixture(name: String): String =
        javaClass.getResourceAsStream("/email-fixtures/linkedin/$name")!!
            .bufferedReader().readText()

    @Test
    fun `supports linkedin alert sender`() {
        assertEquals(true, parser.supports("jobs-noreply@linkedin.com"))
        assertEquals(true, parser.supports("jobs-listings@linkedin.com"))
        assertEquals(false, parser.supports("alerts@indeed.com"))
    }

    @Test
    fun `extracts two postings from sample fixture`() {
        val email = RawEmail(
            messageId = "<m1@linkedin.com>",
            from = "jobs-noreply@linkedin.com",
            subject = "Your job alerts",
            receivedAt = Instant.parse("2026-04-29T10:00:00Z"),
            htmlBody = loadFixture("sample-1.html"),
            textBody = null,
        )

        val parsed = parser.parse(email)
        assertEquals(2, parsed.postings.size)

        val first = parsed.postings[0]
        assertEquals("Senior Backend Engineer", first.title)
        assertEquals("Acme Robotics", first.company)
        assertEquals("Tel Aviv, Israel", first.location)
        assertEquals("https://www.linkedin.com/comm/jobs/view/3940000001/", first.sourceUrl)
        assertEquals("3940000001", first.externalId)
    }
}
