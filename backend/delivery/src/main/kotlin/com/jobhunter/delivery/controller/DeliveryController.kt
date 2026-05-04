package com.jobhunter.delivery.controller

import com.jobhunter.core.domain.MatchState
import com.jobhunter.core.repository.MatchRepository
import com.jobhunter.delivery.dto.DraftedEmail
import com.jobhunter.delivery.dto.SendRequest
import com.jobhunter.delivery.service.CoverLetterService
import com.jobhunter.delivery.service.EmailSendService
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/matches")
class DeliveryController(
  private val draftService: CoverLetterService,
  private val sendService: EmailSendService,
  private val matches: MatchRepository,
) {
  @PostMapping("/{id}/draft")
  fun draft(
    @PathVariable id: Long,
  ): DraftedEmail = draftService.draft(id)

  @PostMapping("/{id}/send")
  fun send(
    @PathVariable id: Long,
    @RequestBody request: SendRequest,
  ) {
    sendService.send(id, subjectOverride = request.subject, bodyOverride = request.body)
  }

  @PostMapping("/{id}/skip")
  fun skip(
    @PathVariable id: Long,
  ) {
    val match = matches.findById(id).orElseThrow {
      IllegalArgumentException("Match $id not found")
    }
    match.state = MatchState.SKIPPED
    match.updatedAt = Instant.now()
    matches.save(match)
  }
}
