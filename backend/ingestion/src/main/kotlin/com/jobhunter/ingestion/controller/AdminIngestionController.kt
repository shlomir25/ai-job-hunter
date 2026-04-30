package com.jobhunter.ingestion.controller

import com.jobhunter.ingestion.config.IngestionProperties
import com.jobhunter.ingestion.service.IngestionRunResult
import com.jobhunter.ingestion.service.IngestionService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/ingestion")
class AdminIngestionController(
    private val service: IngestionService,
    private val props: IngestionProperties,
) {
    @PostMapping("/run-now")
    fun runNow(@RequestParam source: String): IngestionRunResult =
        service.runSource(
            sourceCode = source,
            host = props.host, port = props.port,
            username = props.username, password = props.password,
            maxMessages = props.maxMessagesPerRun,
        )
}
