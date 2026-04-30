package com.jobhunter.ingestion.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("jobhunter.imap")
data class IngestionProperties(
    val host: String = "imap.gmail.com",
    val port: Int = 993,
    val username: String = "",
    val password: String = "",
    val maxMessagesPerRun: Int = 100,
)
