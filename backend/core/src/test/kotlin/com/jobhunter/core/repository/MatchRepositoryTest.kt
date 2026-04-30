package com.jobhunter.core.repository

import com.jobhunter.core.AbstractRepositoryTest
import com.jobhunter.core.domain.Cv
import com.jobhunter.core.domain.JobPosting
import com.jobhunter.core.domain.JobSource
import com.jobhunter.core.domain.Match
import com.jobhunter.core.domain.MatchState
import com.jobhunter.core.domain.SourceType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import kotlin.test.assertEquals

class MatchRepositoryTest : AbstractRepositoryTest() {
    @Autowired lateinit var sources: JobSourceRepository
    @Autowired lateinit var postings: JobPostingRepository
    @Autowired lateinit var cvs: CvRepository
    @Autowired lateinit var matches: MatchRepository

    @Test
    fun `saves and retrieves a match with reasoning JSON`() {
        val source = sources.save(JobSource("S", SourceType.IMAP, true, "{}"))
        val posting = postings.save(JobPosting(
            sourceId = source.id!!, externalId = "E", rawText = "x", capturedAt = Instant.now(),
        ))
        val cv = cvs.save(Cv("d", "d.pdf", "application/pdf", byteArrayOf(0), "x", FloatArray(1024), null, true))

        val saved = matches.save(Match(
            jobPostingId = posting.id!!,
            cvId = cv.id!!,
            cosineSimilarity = 0.78,
            llmScore = 82,
            llmReasoning = """{"strengths":["kotlin"],"gaps":[]}""",
            state = MatchState.READY_FOR_REVIEW,
        ))
        val found = matches.findById(saved.id!!).get()
        assertEquals(82, found.llmScore)
        assertEquals(MatchState.READY_FOR_REVIEW, found.state)
    }

    @Test
    fun `findReadyForReview orders by score desc`() {
        val source = sources.save(JobSource("S2", SourceType.SCRAPER, true, "{}"))
        val cv = cvs.save(Cv("d2", "d.pdf", "application/pdf", byteArrayOf(0), "x", FloatArray(1024), null, true))
        listOf(50, 90, 70).forEachIndexed { i, score ->
            val p = postings.save(JobPosting(
                sourceId = source.id!!, externalId = "E$i", rawText = "x", capturedAt = Instant.now(),
            ))
            matches.save(Match(
                jobPostingId = p.id!!, cvId = cv.id!!, cosineSimilarity = 0.5, llmScore = score,
                state = MatchState.READY_FOR_REVIEW,
            ))
        }
        val ordered = matches.findReadyForReview()
        assertEquals(listOf(90, 70, 50), ordered.map { it.llmScore })
    }
}
