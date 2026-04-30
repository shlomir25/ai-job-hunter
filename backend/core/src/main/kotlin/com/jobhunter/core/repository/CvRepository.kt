package com.jobhunter.core.repository

import com.jobhunter.core.domain.Cv
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface CvRepository : JpaRepository<Cv, Long> {
    @Query("SELECT c FROM Cv c WHERE c.isActive = true")
    fun findActive(): Cv?
}
