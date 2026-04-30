package com.jobhunter.ingestion.parser

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EmailParserRegistryTest {

    private val linkedin = LinkedInAlertParser()
    private val indeed = IndeedAlertParser()
    private val glassdoor = GlassdoorAlertParser()
    private val registry = EmailParserRegistry(listOf(linkedin, indeed, glassdoor))

    @Test
    fun `picks parser whose supports returns true`() {
        assertEquals(linkedin, registry.parserFor("jobs-noreply@linkedin.com"))
        assertEquals(indeed, registry.parserFor("alerts@indeed.com"))
        assertEquals(glassdoor, registry.parserFor("noreply@glassdoor.com"))
    }

    @Test
    fun `returns null for unknown sender`() {
        assertNull(registry.parserFor("random@whatever.com"))
    }
}
