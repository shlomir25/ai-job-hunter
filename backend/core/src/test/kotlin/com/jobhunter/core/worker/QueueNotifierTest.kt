package com.jobhunter.core.worker

import com.jobhunter.core.AbstractRepositoryTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertEquals

class QueueNotifierTest : AbstractRepositoryTest() {
    @Autowired lateinit var jdbc: JdbcTemplate

    @Test
    fun `notify executes against postgres without error`() {
        val notifier = QueueNotifier(jdbc)
        // We cannot easily LISTEN in the same test connection, but we can verify
        // NOTIFY is a no-op (returns void) when there are no listeners — failure
        // would be a SQL exception.
        notifier.notify("queue_event")
        // A sanity SELECT confirms the connection is still healthy.
        assertEquals(1, jdbc.queryForObject("SELECT 1", Int::class.java))
    }
}
