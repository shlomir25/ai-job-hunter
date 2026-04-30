package com.jobhunter.processing.worker

import com.jobhunter.core.AbstractRepositoryTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.springframework.beans.factory.annotation.Autowired
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

class PostgresQueueListenerTest : AbstractRepositoryTest() {
  @Autowired lateinit var dataSource: javax.sql.DataSource

  @Test
  @Timeout(15)
  fun `wakes callback on NOTIFY queue_event`() {
    val woke = CountDownLatch(1)
    val listener = PostgresQueueListener(dataSource) { woke.countDown() }
    listener.start()
    try {
      // Give the listener thread a moment to register its LISTEN.
      Thread.sleep(500)
      // Use a fresh autocommit connection so the NOTIFY isn't trapped in the
      // outer @DataJpaTest rollback transaction (which would never broadcast).
      dataSource.connection.use { conn ->
        conn.autoCommit = true
        conn.createStatement().use { it.execute("NOTIFY queue_event") }
      }
      assertTrue(woke.await(10, TimeUnit.SECONDS), "listener did not fire callback")
    } finally {
      listener.stop()
    }
  }
}
