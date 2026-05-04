package com.jobhunter.matching.controller

import com.jobhunter.core.repository.CvRepository
import com.jobhunter.matching.dto.CvUploadResponse
import com.jobhunter.matching.service.CvService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.time.Instant

@RestController
@RequestMapping("/api/cv")
class CvController(
  private val service: CvService,
  private val cvs: CvRepository,
) {
  @PostMapping
  fun upload(
    @RequestParam(defaultValue = "default") label: String,
    @RequestParam("file") file: MultipartFile,
  ): CvUploadResponse =
    service.upload(
      label = label,
      fileName = file.originalFilename ?: "cv",
      mimeType = file.contentType ?: "application/octet-stream",
      bytes = file.bytes,
    )

  @GetMapping
  fun list(): List<CvListItem> =
    cvs.findAll().map {
      CvListItem(
        id = it.id!!,
        label = it.label,
        fileName = it.fileName,
        isActive = it.isActive,
        createdAt = it.createdAt,
      )
    }

  data class CvListItem(
    val id: Long,
    val label: String,
    val fileName: String,
    val isActive: Boolean,
    val createdAt: Instant,
  )
}
