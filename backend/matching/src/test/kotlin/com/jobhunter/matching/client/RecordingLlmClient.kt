package com.jobhunter.matching.client

import com.jobhunter.core.client.LlmClient

class RecordingLlmClient : LlmClient {
  private val responses = mutableMapOf<String, String>()
  val callLog: MutableList<Pair<String, String>> = mutableListOf()

  fun record(system: String, user: String, response: String) {
    responses[key(system, user)] = response
  }

  fun recordByUser(user: String, response: String) {
    responses["USER::$user"] = response
  }

  override fun chat(system: String, user: String): String {
    callLog += system to user
    return responses[key(system, user)]
      ?: responses["USER::$user"]
      ?: error("No recorded response for user prompt:\n$user")
  }

  override fun chatStructured(system: String, user: String): String = chat(system, user)

  private fun key(system: String, user: String) = "${system.hashCode()}::${user.hashCode()}"
}
