package com.jobhunter.matching.dto

data class CvSummary(
  val skills: List<String>,
  val yearsTotalExperience: Int?,
  val languages: List<String>,
  val pastRoles: List<String>,
  val education: String?,
  val highlights: String?,
)
