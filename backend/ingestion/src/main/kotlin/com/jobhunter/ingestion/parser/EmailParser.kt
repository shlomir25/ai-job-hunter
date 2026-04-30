package com.jobhunter.ingestion.parser

import com.jobhunter.ingestion.dto.ParsedJobAlertEmail
import com.jobhunter.ingestion.dto.RawEmail

interface EmailParser {
    val sourceCode: String
    fun supports(senderAddress: String): Boolean
    fun parse(email: RawEmail): ParsedJobAlertEmail
}
