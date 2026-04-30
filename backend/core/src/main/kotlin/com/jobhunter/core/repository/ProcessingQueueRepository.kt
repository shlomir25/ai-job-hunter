package com.jobhunter.core.repository

import com.jobhunter.core.domain.ProcessingQueueRow
import com.jobhunter.core.domain.QueueState
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ProcessingQueueRepository : JpaRepository<ProcessingQueueRow, Long> {

  @Query(
    value = """
            SELECT * FROM processing_queue
            WHERE state = :state
              AND (next_attempt_at IS NULL OR next_attempt_at <= now())
            ORDER BY id
            LIMIT :batch
            FOR UPDATE SKIP LOCKED
        """,
    nativeQuery = true,
  )
  fun claimNext(@Param("state") state: String, @Param("batch") batch: Int): List<ProcessingQueueRow>

  fun findByState(state: QueueState): List<ProcessingQueueRow>
  fun countByState(state: QueueState): Long
}
