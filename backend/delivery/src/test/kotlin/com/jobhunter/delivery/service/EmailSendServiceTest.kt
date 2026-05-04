package com.jobhunter.delivery.service

import com.icegreen.greenmail.junit5.GreenMailExtension
import com.icegreen.greenmail.util.ServerSetupTest
import com.jobhunter.core.AbstractRepositoryTest
import com.jobhunter.core.domain.Cv
import com.jobhunter.core.domain.JobPosting
import com.jobhunter.core.domain.JobSource
import com.jobhunter.core.domain.Match
import com.jobhunter.core.domain.MatchState
import com.jobhunter.core.domain.SourceType
import com.jobhunter.core.repository.CvRepository
import com.jobhunter.core.repository.EmailSendRecordRepository
import com.jobhunter.core.repository.JobPostingRepository
import com.jobhunter.core.repository.JobSourceRepository
import com.jobhunter.core.repository.MatchRepository
import com.jobhunter.delivery.config.DeliveryProperties
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.JavaMailSenderImpl
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class EmailSendServiceTest : AbstractRepositoryTest() {

  companion object {
    @RegisterExtension
    @JvmStatic
    val greenMail: GreenMailExtension = GreenMailExtension(ServerSetupTest.SMTP)
  }

  @Autowired lateinit var sources: JobSourceRepository

  @Autowired lateinit var postings: JobPostingRepository

  @Autowired lateinit var cvs: CvRepository

  @Autowired lateinit var matches: MatchRepository

  @Autowired lateinit var sends: EmailSendRecordRepository

  private fun mailSender(): JavaMailSender =
    JavaMailSenderImpl().apply {
      host = ServerSetupTest.SMTP.bindAddress
      port = ServerSetupTest.SMTP.port
    }

  @Test
  fun `sends email with attachment, writes record, transitions match to SENT`() {
    val src = sources.save(JobSource("S", SourceType.IMAP, true, "{}"))
    val post = postings.save(
      JobPosting(
        sourceId = src.id!!,
        externalId = "E",
        rawText = "x",
        title = "Backend Engineer",
        company = "Acme",
        contactEmail = "jobs@acme.com",
        capturedAt = Instant.now(),
      ),
    )
    val cvBytes = "PDFBYTES".toByteArray()
    val cv = cvs.save(
      Cv("default", "shlomi.pdf", "application/pdf", cvBytes, "CV text", FloatArray(1024), null, isActive = true),
    )
    val match = matches.save(
      Match(
        jobPostingId = post.id!!,
        cvId = cv.id!!,
        cosineSimilarity = 0.8,
        llmScore = 85,
        state = MatchState.DRAFTED,
        draftSubject = "Application for Backend Engineer",
        draftBody = "Hi, I'd like to apply.",
      ),
    )

    val service = EmailSendService(
      matches = matches,
      postings = postings,
      cvs = cvs,
      sends = sends,
      mailSender = mailSender(),
      validator = EmailValidator(DeliveryProperties(fromAddress = "me@example.com")),
      props = DeliveryProperties(fromAddress = "me@example.com", candidateName = "Shlomi"),
    )
    service.send(match.id!!, subjectOverride = null, bodyOverride = null)

    assertEquals(MatchState.SENT, matches.findById(match.id!!).get().state)
    val record = sends.findByMatchId(match.id!!)
    assertNotNull(record)
    assertEquals("jobs@acme.com", record.toAddress)
    assertEquals("SENT", record.status)
    val received = greenMail.receivedMessages
    assertEquals(1, received.size)
    assertEquals("Application for Backend Engineer", received[0].subject)
  }
}
