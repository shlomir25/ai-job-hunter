package com.jobhunter.core.domain

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "email_send_record")
class EmailSendRecord(
    @Column(name = "match_id", nullable = false, unique = true)
    var matchId: Long,

    @Column(name = "cv_id", nullable = false)
    var cvId: Long,

    @Column(name = "to_address", nullable = false, length = 255)
    var toAddress: String,

    @Column(name = "subject", nullable = false, length = 500)
    var subject: String,

    @Column(name = "body", nullable = false, columnDefinition = "text")
    var body: String,

    @Column(name = "attachment_filename", length = 255)
    var attachmentFilename: String? = null,

    @Column(name = "sent_at", nullable = false)
    var sentAt: Instant,

    @Column(name = "smtp_message_id", length = 255)
    var smtpMessageId: String? = null,

    @Column(name = "status", nullable = false, length = 20)
    var status: String,

    @Column(name = "failure_reason", columnDefinition = "text")
    var failureReason: String? = null,

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
