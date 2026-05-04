package com.jobhunter.delivery.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("jobhunter.delivery")
data class DeliveryProperties(
  val fromAddress: String = "",
  val candidateName: String = "Candidate",
  val denyList: List<String> = listOf(
    "noreply@",
    "no-reply@",
    "donotreply@",
    "do-not-reply@",
  ),
)
