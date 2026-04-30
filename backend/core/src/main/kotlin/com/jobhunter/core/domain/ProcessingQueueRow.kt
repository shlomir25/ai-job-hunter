package com.jobhunter.core.domain

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "processing_queue")
class ProcessingQueueRow(
    @Column(name = "job_posting_id", nullable = false)
    var jobPostingId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 30)
    var state: QueueState,

    @Column(name = "attempts", nullable = false)
    var attempts: Int = 0,

    @Column(name = "last_error", columnDefinition = "text")
    var lastError: String? = null,

    @Column(name = "next_attempt_at")
    var nextAttemptAt: Instant? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
