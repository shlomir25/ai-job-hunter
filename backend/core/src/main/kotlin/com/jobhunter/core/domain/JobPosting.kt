package com.jobhunter.core.domain

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(
    name = "job_posting",
    uniqueConstraints = [UniqueConstraint(columnNames = ["source_id", "external_id"])],
)
class JobPosting(
    @Column(name = "source_id", nullable = false)
    var sourceId: Long,

    @Column(name = "external_id", nullable = false, length = 255)
    var externalId: String,

    @Column(name = "raw_text", nullable = false, columnDefinition = "text")
    var rawText: String,

    @Column(name = "raw_html", columnDefinition = "text")
    var rawHtml: String? = null,

    @Column(name = "source_url", columnDefinition = "text")
    var sourceUrl: String? = null,

    @Column(name = "title", length = 500)
    var title: String? = null,

    @Column(name = "company", length = 255)
    var company: String? = null,

    @Column(name = "location", length = 255)
    var location: String? = null,

    @Column(name = "is_remote")
    var isRemote: Boolean? = null,

    @Column(name = "language", length = 2)
    var language: String? = null,

    @Column(name = "contact_email", length = 255)
    var contactEmail: String? = null,

    @Column(name = "apply_url", columnDefinition = "text")
    var applyUrl: String? = null,

    @Column(name = "description", columnDefinition = "text")
    var description: String? = null,

    @Column(name = "requirements", columnDefinition = "text")
    var requirements: String? = null,

    @Column(name = "salary_text", length = 255)
    var salaryText: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "categories", nullable = false, columnDefinition = "jsonb")
    var categories: List<Category> = emptyList(),

    @Column(name = "posted_at")
    var postedAt: Instant? = null,

    @Column(name = "captured_at", nullable = false)
    var capturedAt: Instant = Instant.now(),

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
