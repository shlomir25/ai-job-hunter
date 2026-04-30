package com.jobhunter.core.repository

import com.jobhunter.core.domain.Match
import com.jobhunter.core.domain.MatchState
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface MatchRepository : JpaRepository<Match, Long> {
  fun findByState(state: MatchState): List<Match>

  @Query(
    """
        SELECT m FROM Match m
        WHERE m.state = com.jobhunter.core.domain.MatchState.READY_FOR_REVIEW
        ORDER BY m.llmScore DESC NULLS LAST, m.id ASC
    """,
  )
  fun findReadyForReview(): List<Match>
}
