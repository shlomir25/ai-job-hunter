package com.jobhunter.core.worker

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class QueueNotifier(private val jdbc: JdbcTemplate) {
  /** Send a Postgres NOTIFY on the given channel. No payload. */
  fun notify(channel: String) {
    require(channel.matches(Regex("[a-z_][a-z0-9_]*"))) {
      "channel must be a safe identifier; got '$channel'"
    }
    jdbc.execute("NOTIFY $channel")
  }
}
