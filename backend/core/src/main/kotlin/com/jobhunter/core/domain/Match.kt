package com.jobhunter.core.domain

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(
    name = "match",
    uniqueConstraints = [UniqueConstraint(columnNames = ["job_posting_id", "cv_id"])],
)
class Match(
    @Column(name = "job_posting_id", nullable = false)
    var jobPostingId: Long,

    @Column(name = "cv_id", nullable = false)
    var cvId: Long,

    @Column(name = "cosine_similarity", nullable = false)
    var cosineSimilarity: Double,

    @Column(name = "llm_score")
    var llmScore: Int? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "llm_reasoning", columnDefinition = "jsonb")
    var llmReasoning: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 30)
    var state: MatchState,

    @Column(name = "draft_subject", length = 500)
    var draftSubject: String? = null,

    @Column(name = "draft_body", columnDefinition = "text")
    var draftBody: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
