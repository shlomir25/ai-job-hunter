package com.jobhunter.core.domain

import com.jobhunter.core.jpa.PgVectorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.Type
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "cv")
class Cv(
  @Column(name = "label", nullable = false, length = 100)
  var label: String,

  @Column(name = "file_name", nullable = false, length = 255)
  var fileName: String,

  @Column(name = "mime_type", nullable = false, length = 100)
  var mimeType: String,

  @Column(name = "file_bytes", nullable = false)
  var fileBytes: ByteArray,

  @Column(name = "parsed_text", nullable = false, columnDefinition = "text")
  var parsedText: String,

  @Type(PgVectorType::class)
  @Column(name = "embedding", nullable = false, columnDefinition = "vector(1024)")
  var embedding: FloatArray,

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "structured_summary", columnDefinition = "jsonb")
  var structuredSummary: String? = null,

  @Column(name = "is_active", nullable = false)
  var isActive: Boolean = false,

  @Column(name = "created_at", nullable = false, updatable = false)
  var createdAt: Instant = Instant.now(),

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  var id: Long? = null,
)
