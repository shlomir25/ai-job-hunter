package com.jobhunter.processing.service

import com.jobhunter.core.client.LlmClient
import com.jobhunter.processing.client.RecordingLlmClient
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EmailExtractorTest {

    @Test
    fun `picks first valid email when present`() {
        val llm = mockk<LlmClient>(relaxed = true)
        val extractor = EmailExtractor(llm)
        val text = "Apply: jobs@acme.com or careers@spam.net"
        assertEquals("jobs@acme.com", extractor.extract(text, companyHint = "Acme"))
        verify(exactly = 0) { llm.chat(any(), any()) }
    }

    @Test
    fun `prefers email matching company domain`() {
        val llm = mockk<LlmClient>(relaxed = true)
        val extractor = EmailExtractor(llm)
        val text = "Submit to noreply@indeed.com or jobs@acme.com"
        assertEquals("jobs@acme.com", extractor.extract(text, companyHint = "Acme"))
    }

    @Test
    fun `falls back to LLM when no regex matches found`() {
        val llm = RecordingLlmClient()
        llm.recordByUser("No email visible. Reply with the email or 'null'.", "careers@beta.com")
        val extractor = EmailExtractor(llm)
        val result = extractor.extract("No email visible. Reply with the email or 'null'.", companyHint = "Beta")
        assertEquals("careers@beta.com", result)
    }

    @Test
    fun `rejects LLM hallucination that does not pass regex`() {
        val llm = RecordingLlmClient()
        llm.recordByUser("Some text", "thisisnotanemail")
        val extractor = EmailExtractor(llm)
        assertNull(extractor.extract("Some text", companyHint = null))
    }

    @Test
    fun `returns null when LLM says null`() {
        val llm = RecordingLlmClient()
        llm.recordByUser("Posting body", "null")
        val extractor = EmailExtractor(llm)
        assertNull(extractor.extract("Posting body", companyHint = null))
    }
}
