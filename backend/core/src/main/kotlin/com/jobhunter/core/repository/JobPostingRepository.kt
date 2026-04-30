package com.jobhunter.core.repository

import com.jobhunter.core.domain.JobPosting
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface JobPostingRepository : JpaRepository<JobPosting, Long> {
  fun findBySourceIdAndExternalId(sourceId: Long, externalId: String): JobPosting?
}
