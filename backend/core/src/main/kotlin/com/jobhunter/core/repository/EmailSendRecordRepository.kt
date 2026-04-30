package com.jobhunter.core.repository

import com.jobhunter.core.domain.EmailSendRecord
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface EmailSendRecordRepository : JpaRepository<EmailSendRecord, Long> {
    fun findByMatchId(matchId: Long): EmailSendRecord?
}
