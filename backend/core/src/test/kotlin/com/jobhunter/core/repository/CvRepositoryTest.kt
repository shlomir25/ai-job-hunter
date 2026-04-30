package com.jobhunter.core.repository

import com.jobhunter.core.AbstractRepositoryTest
import com.jobhunter.core.domain.Cv
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CvRepositoryTest : AbstractRepositoryTest() {
  @Autowired lateinit var repository: CvRepository

  @Test
  fun `saves and retrieves a CV with bytes and embedding`() {
    val saved = repository.save(
      Cv(
        label = "default",
        fileName = "shlomi.pdf",
        mimeType = "application/pdf",
        fileBytes = byteArrayOf(1, 2, 3, 4),
        parsedText = "Kotlin engineer with 7 years experience.",
        embedding = FloatArray(1024) { 0.1f },
        structuredSummary = """{"skills":["kotlin"],"years":7}""",
        isActive = true,
      ),
    )
    assertNotNull(saved.id)
    val found = repository.findById(saved.id!!).get()
    assertEquals("default", found.label)
    assertEquals(4, found.fileBytes.size)
    assertEquals(1024, found.embedding.size)
    assertEquals(true, found.isActive)
  }

  @Test
  fun `findActive returns the single active CV`() {
    repository.save(Cv("a", "a.pdf", "application/pdf", byteArrayOf(0), "x", FloatArray(1024), null, false))
    val active = repository.save(Cv("b", "b.pdf", "application/pdf", byteArrayOf(0), "x", FloatArray(1024), null, true))
    assertEquals(active.id, repository.findActive()?.id)
  }
}
