package com.jobhunter.core.repository

import com.jobhunter.core.AbstractRepositoryTest
import com.jobhunter.core.domain.JobPosting
import com.jobhunter.core.domain.JobSource
import com.jobhunter.core.domain.PostingEmbedding
import com.jobhunter.core.domain.SourceType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import kotlin.test.assertEquals

class PostingEmbeddingRepositoryTest : AbstractRepositoryTest() {
  @Autowired lateinit var sources: JobSourceRepository

  @Autowired lateinit var postings: JobPostingRepository

  @Autowired lateinit var embeddings: PostingEmbeddingRepository

  @Test
  fun `roundtrips a 1024-dim vector`() {
    val source = sources.save(JobSource("S", SourceType.IMAP, true, "{}"))
    val posting = postings.save(
      JobPosting(
        sourceId = source.id!!,
        externalId = "E",
        rawText = "x",
        capturedAt = Instant.now(),
      ),
    )
    val vec = FloatArray(1024) { i -> i / 1024f }
    val saved = embeddings.save(
      PostingEmbedding(
        jobPostingId = posting.id!!,
        embedding = vec,
        modelName = "bge-m3",
      ),
    )
    val found = embeddings.findById(saved.jobPostingId).get()
    assertEquals(vec.toList(), found.embedding.toList())
    assertEquals("bge-m3", found.modelName)
  }
}
