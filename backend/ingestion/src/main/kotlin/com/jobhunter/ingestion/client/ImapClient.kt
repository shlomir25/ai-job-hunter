package com.jobhunter.ingestion.client

import com.jobhunter.ingestion.dto.RawEmail

interface ImapClient {
    fun fetch(
        host: String,
        port: Int,
        username: String,
        password: String,
        folder: String,
        fromFilter: String,
        maxMessages: Int,
    ): List<RawEmail>
}
