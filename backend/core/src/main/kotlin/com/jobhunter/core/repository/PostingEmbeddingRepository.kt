package com.jobhunter.core.repository

import com.jobhunter.core.domain.PostingEmbedding
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PostingEmbeddingRepository : JpaRepository<PostingEmbedding, Long> {

  /**
   * Cosine similarity (1 - cosine distance) between the posting's embedding and the supplied vector.
   * Vector arrives as a Postgres-cast string literal "[0.1,0.2,...]".
   */
  @Query(
    value = "SELECT 1 - (embedding <=> CAST(:vec AS vector)) FROM posting_embedding WHERE job_posting_id = :postingId",
    nativeQuery = true,
  )
  fun cosineToVector(@Param("postingId") postingId: Long, @Param("vec") vec: String): Double?
}
