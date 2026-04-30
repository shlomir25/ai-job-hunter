package com.jobhunter.ingestion.controller

import com.jobhunter.ingestion.config.IngestionProperties
import com.jobhunter.ingestion.service.IngestionRunResult
import com.jobhunter.ingestion.service.IngestionService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class AdminIngestionControllerTest {

  private val service: IngestionService = mockk()
  private val props = IngestionProperties("h", 993, "u", "p", 50)
  private val controller = AdminIngestionController(service, props)
  private val mvc: MockMvc = MockMvcBuilders.standaloneSetup(controller).build()

  @Test
  fun `POST run-now triggers source by code`() {
    every { service.runSource("IMAP_LINKEDIN_ALERTS", "h", 993, "u", "p", 50) } returns IngestionRunResult(2, 1)

    mvc.perform(
      post("/api/admin/ingestion/run-now")
        .param("source", "IMAP_LINKEDIN_ALERTS")
        .contentType(MediaType.APPLICATION_JSON),
    )
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.emailsFetched").value(2))
      .andExpect(jsonPath("$.postingsCreated").value(1))
  }

  @Test
  fun `POST run-now returns 400 when source missing`() {
    mvc.perform(post("/api/admin/ingestion/run-now"))
      .andExpect(status().isBadRequest)
  }
}
