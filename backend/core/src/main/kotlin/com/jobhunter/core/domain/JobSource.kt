package com.jobhunter.core.domain

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "job_source")
class JobSource(
    @Column(name = "code", nullable = false, unique = true, length = 50)
    var code: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    var type: SourceType,

    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = true,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", nullable = false, columnDefinition = "jsonb")
    var config: String = "{}",

    @Column(name = "last_run_at")
    var lastRunAt: Instant? = null,

    @Column(name = "last_run_status", length = 20)
    var lastRunStatus: String? = null,

    @Column(name = "last_run_error")
    var lastRunError: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
