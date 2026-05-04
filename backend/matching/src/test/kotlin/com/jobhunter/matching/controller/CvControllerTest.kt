package com.jobhunter.matching.controller

import com.jobhunter.core.repository.CvRepository
import com.jobhunter.matching.dto.CvUploadResponse
import com.jobhunter.matching.service.CvService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class CvControllerTest {

  private val service: CvService = mockk()
  private val cvs: CvRepository = mockk()
  private val controller = CvController(service, cvs)
  private val mvc: MockMvc = MockMvcBuilders.standaloneSetup(controller).build()

  @Test
  fun `POST cv accepts multipart upload`() {
    every {
      service.upload(
        label = "default",
        fileName = "cv.pdf",
        mimeType = "application/pdf",
        bytes = any(),
        overrideText = null,
      )
    } returns CvUploadResponse(id = 1, label = "default", parsedTextLength = 100, skills = listOf("Kotlin"))

    val file = MockMultipartFile("file", "cv.pdf", "application/pdf", "PDFBYTES".toByteArray())

    mvc.perform(multipart("/api/cv").file(file).param("label", "default"))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.id").value(1))
      .andExpect(jsonPath("$.label").value("default"))

    verify {
      service.upload(
        label = "default",
        fileName = "cv.pdf",
        mimeType = "application/pdf",
        bytes = any(),
        overrideText = null,
      )
    }
  }

  @Test
  fun `GET cv lists all CVs`() {
    every { cvs.findAll() } returns emptyList()
    mvc.perform(get("/api/cv"))
      .andExpect(status().isOk)
  }
}
