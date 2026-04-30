package com.jobhunter.core.client

interface LlmClient {
    fun chat(system: String, user: String): String

    /**
     * Like [chat], but expects the response to be a JSON object/array.
     * Strips common markdown fences before returning.
     * The caller deserializes with Jackson against their own schema.
     */
    fun chatStructured(system: String, user: String): String
}
