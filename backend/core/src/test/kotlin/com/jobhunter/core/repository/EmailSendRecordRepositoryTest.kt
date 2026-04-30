package com.jobhunter.core.repository

import com.jobhunter.core.AbstractRepositoryTest
import com.jobhunter.core.domain.Cv
import com.jobhunter.core.domain.EmailSendRecord
import com.jobhunter.core.domain.JobPosting
import com.jobhunter.core.domain.JobSource
import com.jobhunter.core.domain.Match
import com.jobhunter.core.domain.MatchState
import com.jobhunter.core.domain.SourceType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import kotlin.test.assertEquals

class EmailSendRecordRepositoryTest : AbstractRepositoryTest() {
  @Autowired lateinit var sources: JobSourceRepository

  @Autowired lateinit var postings: JobPostingRepository

  @Autowired lateinit var cvs: CvRepository

  @Autowired lateinit var matches: MatchRepository

  @Autowired lateinit var sends: EmailSendRecordRepository

  @Test
  fun `saves and retrieves a send record`() {
    val src = sources.save(JobSource("S", SourceType.IMAP, true, "{}"))
    val post = postings.save(
      JobPosting(
        sourceId = src.id!!,
        externalId = "E",
        rawText = "x",
        capturedAt = Instant.now(),
      ),
    )
    val cv = cvs.save(Cv("d", "d.pdf", "application/pdf", byteArrayOf(0), "x", FloatArray(1024), null, true))
    val match = matches.save(
      Match(
        jobPostingId = post.id!!,
        cvId = cv.id!!,
        cosineSimilarity = 0.8,
        llmScore = 85,
        state = MatchState.SENT,
      ),
    )
    val saved = sends.save(
      EmailSendRecord(
        matchId = match.id!!,
        cvId = cv.id!!,
        toAddress = "jobs@acme.com",
        subject = "Application",
        body = "Hi",
        attachmentFilename = "shlomi.pdf",
        sentAt = Instant.now(),
        smtpMessageId = "<abc@gmail.com>",
        status = "SENT",
      ),
    )
    val found = sends.findById(saved.id!!).get()
    assertEquals("jobs@acme.com", found.toAddress)
    assertEquals("SENT", found.status)
  }
}
