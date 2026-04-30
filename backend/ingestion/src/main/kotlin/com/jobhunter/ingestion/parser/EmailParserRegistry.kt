package com.jobhunter.ingestion.parser

import org.springframework.stereotype.Component

@Component
class EmailParserRegistry(private val parsers: List<EmailParser>) {
    fun parserFor(senderAddress: String): EmailParser? =
        parsers.firstOrNull { it.supports(senderAddress) }

    fun byCode(sourceCode: String): EmailParser? =
        parsers.firstOrNull { it.sourceCode == sourceCode }
}
