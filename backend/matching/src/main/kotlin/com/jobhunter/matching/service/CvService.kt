package com.jobhunter.matching.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.jobhunter.core.client.EmbeddingClient
import com.jobhunter.core.client.LlmClient
import com.jobhunter.core.domain.Cv
import com.jobhunter.core.repository.CvRepository
import com.jobhunter.matching.dto.CvUploadResponse
import com.jobhunter.matching.prompt.CvSummaryPromptBuilder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CvService(
  private val cvs: CvRepository,
  private val parseService: CvParseService,
  private val summaryBuilder: CvSummaryPromptBuilder,
  private val llm: LlmClient,
  private val embeddingClient: EmbeddingClient,
  private val mapper: ObjectMapper,
) {

  @Transactional
  fun upload(
    label: String,
    fileName: String,
    mimeType: String,
    bytes: ByteArray,
    overrideText: String? = null,
  ): CvUploadResponse {
    val parsedText = overrideText ?: parseService.extract(bytes, mimeType, fileName)
    require(parsedText.isNotBlank()) { "CV text extraction returned empty" }

    val summary = summaryBuilder.invoke(llm, parsedText, mapper)
    val embedding = embeddingClient.embed(parsedText)
    require(embedding.size == 1024) { "Expected 1024-dim CV embedding, got ${embedding.size}" }

    cvs.findActive()?.let {
      it.isActive = false
      cvs.saveAndFlush(it)
    }

    val saved = cvs.save(
      Cv(
        label = label,
        fileName = fileName,
        mimeType = mimeType,
        fileBytes = bytes,
        parsedText = parsedText,
        embedding = embedding,
        structuredSummary = mapper.writeValueAsString(summary),
        isActive = true,
      ),
    )

    return CvUploadResponse(
      id = saved.id!!,
      label = saved.label,
      parsedTextLength = parsedText.length,
      skills = summary.skills,
    )
  }
}
