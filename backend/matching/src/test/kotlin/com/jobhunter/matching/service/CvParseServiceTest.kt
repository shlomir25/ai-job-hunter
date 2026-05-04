package com.jobhunter.matching.service

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class CvParseServiceTest {
  private val service = CvParseService()

  @Test
  fun `extracts text from a PDF`() {
    val bytes = javaClass.getResourceAsStream("/cv-fixtures/sample.pdf")!!.readBytes()
    val text = service.extract(bytes, mimeType = "application/pdf", fileName = "sample.pdf")
    assertTrue(text.isNotBlank(), "Extracted text should be non-empty")
  }

  @Test
  fun `auto-detects mime type from bytes when null`() {
    val bytes = javaClass.getResourceAsStream("/cv-fixtures/sample.pdf")!!.readBytes()
    val text = service.extract(bytes, mimeType = null, fileName = "sample.pdf")
    assertTrue(text.isNotBlank())
  }
}
