package com.jobhunter.matching.dto

data class CvUploadResponse(
  val id: Long,
  val label: String,
  val parsedTextLength: Int,
  val skills: List<String>,
)
