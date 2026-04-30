package com.jobhunter.processing.worker

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.postgresql.PGConnection
import javax.sql.DataSource
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

private val log = KotlinLogging.logger {}

/**
 * Holds a dedicated Postgres connection in LISTEN mode on the `queue_event` channel.
 * On each NOTIFY, calls [onNotify], which wakes the worker scheduler.
 */
class PostgresQueueListener(
    private val dataSource: DataSource,
    private val onNotify: () -> Unit,
) {
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null

    @PostConstruct
    fun start() {
        if (!running.compareAndSet(false, true)) return
        thread = thread(name = "queue-listener", isDaemon = true) { loop() }
    }

    @PreDestroy
    fun stop() {
        if (!running.compareAndSet(true, false)) return
        thread?.interrupt()
    }

    private fun loop() {
        while (running.get()) {
            try {
                dataSource.connection.use { conn ->
                    conn.autoCommit = true
                    conn.createStatement().use { it.execute("LISTEN queue_event") }
                    val pg = conn.unwrap(PGConnection::class.java)
                    while (running.get()) {
                        val notifications = pg.getNotifications(5_000)
                        if (notifications != null && notifications.isNotEmpty()) {
                            try {
                                onNotify()
                            } catch (e: Exception) {
                                log.warn(e) { "queue_event handler failed" }
                            }
                        }
                    }
                }
            } catch (e: InterruptedException) {
                // shutting down
                return
            } catch (e: Exception) {
                log.warn(e) { "Listener connection error; reconnecting in 5s" }
                Thread.sleep(5_000)
            }
        }
    }
}
