package com.jobhunter.delivery.service

import com.jobhunter.core.domain.EmailSendRecord
import com.jobhunter.core.domain.Match
import com.jobhunter.core.domain.MatchState
import com.jobhunter.core.repository.CvRepository
import com.jobhunter.core.repository.EmailSendRecordRepository
import com.jobhunter.core.repository.JobPostingRepository
import com.jobhunter.core.repository.MatchRepository
import com.jobhunter.delivery.config.DeliveryProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.mail.internet.MimeMessage
import org.springframework.core.io.ByteArrayResource
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

private val log = KotlinLogging.logger {}

@Service
class EmailSendService(
  private val matches: MatchRepository,
  private val postings: JobPostingRepository,
  private val cvs: CvRepository,
  private val sends: EmailSendRecordRepository,
  private val mailSender: JavaMailSender,
  private val validator: EmailValidator,
  private val props: DeliveryProperties,
) {

  @Transactional
  fun send(matchId: Long, subjectOverride: String?, bodyOverride: String?) {
    val match = matches.findById(matchId).orElseThrow {
      IllegalArgumentException("Match $matchId not found")
    }
    if (match.state == MatchState.SENT) {
      throw IllegalStateException("Match $matchId already SENT")
    }
    require(match.state == MatchState.DRAFTED || match.state == MatchState.SEND_FAILED) {
      "Cannot send a match in state ${match.state}; draft it first"
    }

    val posting = postings.findById(match.jobPostingId).orElseThrow {
      IllegalStateException("Posting ${match.jobPostingId} not found")
    }
    val toAddress = posting.contactEmail
      ?: throw IllegalStateException("Posting ${posting.id} has no contact email; cannot send")

    if (!validator.isValid(toAddress)) {
      recordFailure(match)
      throw IllegalArgumentException("Address $toAddress failed validation")
    }

    val cv = cvs.findById(match.cvId).orElseThrow {
      IllegalStateException("CV ${match.cvId} not found")
    }

    val subject = subjectOverride ?: match.draftSubject
      ?: throw IllegalStateException("No subject; draft the match first")
    val body = bodyOverride ?: match.draftBody
      ?: throw IllegalStateException("No body; draft the match first")

    val mime: MimeMessage = mailSender.createMimeMessage()
    val helper = MimeMessageHelper(mime, true, "UTF-8")
    helper.setFrom(props.fromAddress)
    helper.setTo(toAddress)
    helper.setSubject(subject)
    helper.setText(body, false)
    helper.addAttachment(cv.fileName, ByteArrayResource(cv.fileBytes))

    try {
      mailSender.send(mime)
      sends.save(
        EmailSendRecord(
          matchId = match.id!!,
          cvId = cv.id!!,
          toAddress = toAddress,
          subject = subject,
          body = body,
          attachmentFilename = cv.fileName,
          sentAt = Instant.now(),
          smtpMessageId = mime.messageID,
          status = "SENT",
        ),
      )
      match.state = MatchState.SENT
      match.updatedAt = Instant.now()
      matches.save(match)
    } catch (e: Exception) {
      log.warn(e) { "SMTP send failed for match $matchId" }
      recordFailure(match)
      throw e
    }
  }

  private fun recordFailure(match: Match) {
    match.state = MatchState.SEND_FAILED
    match.updatedAt = Instant.now()
    matches.save(match)
  }
}
