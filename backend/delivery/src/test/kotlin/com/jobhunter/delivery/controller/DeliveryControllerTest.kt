package com.jobhunter.delivery.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.jobhunter.core.domain.Match
import com.jobhunter.core.domain.MatchState
import com.jobhunter.core.repository.MatchRepository
import com.jobhunter.delivery.dto.DraftedEmail
import com.jobhunter.delivery.service.CoverLetterService
import com.jobhunter.delivery.service.EmailSendService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.Optional

class DeliveryControllerTest {

  private val draftService: CoverLetterService = mockk()
  private val sendService: EmailSendService = mockk(relaxed = true)
  private val matches: MatchRepository = mockk(relaxed = true)
  private val controller = DeliveryController(draftService, sendService, matches)
  private val mvc: MockMvc = MockMvcBuilders.standaloneSetup(controller).build()
  private val mapper = ObjectMapper().registerKotlinModule()

  @Test
  fun `POST draft returns drafted email`() {
    every { draftService.draft(42) } returns DraftedEmail("subj", "body")
    mvc.perform(post("/api/matches/42/draft"))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.subject").value("subj"))
      .andExpect(jsonPath("$.body").value("body"))
  }

  @Test
  fun `POST send delegates to sendService`() {
    mvc.perform(
      post("/api/matches/42/send")
        .contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsString(mapOf("subject" to "S", "body" to "B"))),
    ).andExpect(status().isOk)
    verify { sendService.send(42, "S", "B") }
  }

  @Test
  fun `POST skip transitions match to SKIPPED`() {
    val m = Match(
      jobPostingId = 1,
      cvId = 1,
      cosineSimilarity = 0.5,
      llmScore = 60,
      state = MatchState.READY_FOR_REVIEW,
      id = 7,
      createdAt = Instant.now(),
      updatedAt = Instant.now(),
    )
    every { matches.findById(7) } returns Optional.of(m)
    every { matches.save(any()) } answers { firstArg() }

    mvc.perform(post("/api/matches/7/skip")).andExpect(status().isOk)

    verify {
      matches.save(match<Match> { it.state == MatchState.SKIPPED })
    }
  }
}
