package com.jobhunter.matching.controller

import com.jobhunter.core.domain.MatchState
import com.jobhunter.matching.dto.MatchView
import com.jobhunter.matching.dto.PostingView
import com.jobhunter.matching.service.MatchService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant

class MatchControllerTest {

  private val service: MatchService = mockk()
  private val controller = MatchController(service)
  private val mvc: MockMvc = MockMvcBuilders.standaloneSetup(controller).build()

  @Test
  fun `GET matches returns ready list`() {
    every { service.listReady() } returns listOf(
      MatchView(
        id = 1,
        state = MatchState.READY_FOR_REVIEW,
        llmScore = 85,
        cosineSimilarity = 0.8,
        reasoning = null,
        posting = PostingView(
          id = 1,
          title = "Backend",
          company = "Acme",
          location = "TLV",
          isRemote = false,
          language = "en",
          description = null,
          requirements = null,
          contactEmail = "jobs@acme.com",
          applyUrl = null,
          sourceUrl = null,
        ),
        createdAt = Instant.now(),
      ),
    )
    mvc.perform(get("/api/matches"))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$[0].llmScore").value(85))
      .andExpect(jsonPath("$[0].posting.contactEmail").value("jobs@acme.com"))
  }

  @Test
  fun `GET matches by id returns 404 when missing`() {
    every { service.get(99) } returns null
    mvc.perform(get("/api/matches/99"))
      .andExpect(status().isNotFound)
  }
}
