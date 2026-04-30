package com.jobhunter.core.client

interface EmbeddingClient {
  fun embed(text: String): FloatArray
}
