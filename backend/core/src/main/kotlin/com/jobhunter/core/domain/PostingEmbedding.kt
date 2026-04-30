package com.jobhunter.core.domain

import com.jobhunter.core.jpa.PgVectorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Type
import java.time.Instant

@Entity
@Table(name = "posting_embedding")
class PostingEmbedding(
  @Id
  @Column(name = "job_posting_id")
  var jobPostingId: Long,

  @Type(PgVectorType::class)
  @Column(name = "embedding", nullable = false, columnDefinition = "vector(1024)")
  var embedding: FloatArray,

  @Column(name = "model_name", nullable = false, length = 100)
  var modelName: String,

  @Column(name = "created_at", nullable = false, updatable = false)
  var createdAt: Instant = Instant.now(),
)
