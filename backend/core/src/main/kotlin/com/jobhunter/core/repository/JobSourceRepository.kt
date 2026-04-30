package com.jobhunter.core.repository

import com.jobhunter.core.domain.JobSource
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface JobSourceRepository : JpaRepository<JobSource, Long> {
    fun findByCode(code: String): JobSource?
    fun findByEnabledTrue(): List<JobSource>
}
