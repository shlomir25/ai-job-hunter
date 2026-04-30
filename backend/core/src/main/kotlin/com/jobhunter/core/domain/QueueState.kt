package com.jobhunter.core.domain

enum class QueueState {
    INGESTED, PARSED, CLASSIFIED, EMBEDDED, MATCHED,
    IRRELEVANT, OUT_OF_SCOPE, FAILED,
}
