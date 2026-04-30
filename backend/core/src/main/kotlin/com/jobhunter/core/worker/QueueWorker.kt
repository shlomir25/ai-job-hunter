package com.jobhunter.core.worker

import com.jobhunter.core.domain.QueueState
import com.jobhunter.core.repository.ProcessingQueueRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import kotlin.math.min
import kotlin.math.pow

private val log = KotlinLogging.logger {}

abstract class QueueWorker(
    private val inputState: QueueState,
    private val outputState: QueueState,
    private val queue: ProcessingQueueRepository,
    txManager: PlatformTransactionManager,
    private val maxAttempts: Int,
    private val baseBackoffSeconds: Long = 5,
    private val maxBackoffSeconds: Long = 80,
) {
    private val tx = TransactionTemplate(txManager)

    /**
     * Subclasses override this with the actual work for one row.
     * Throw to indicate failure; the framework handles retries and state.
     */
    abstract fun process(jobPostingId: Long)

    /** Claim a small batch and process each row in its own transaction. */
    fun runOnce(batchSize: Int = 5) {
        val claimed = tx.execute { queue.claimNext(inputState.name, batchSize) } ?: return
        for (row in claimed) {
            handleOne(row.id!!, row.jobPostingId)
        }
    }

    private fun handleOne(queueId: Long, jobPostingId: Long) {
        try {
            process(jobPostingId)
            tx.executeWithoutResult {
                val r = queue.findById(queueId).get()
                if (r.state == inputState) {
                    r.state = outputState
                    r.attempts = 0
                    r.lastError = null
                    r.nextAttemptAt = null
                    r.updatedAt = Instant.now()
                    queue.save(r)
                }
                // else: subclass already moved to a terminal state in process(); leave it.
            }
        } catch (e: Exception) {
            log.warn(e) { "QueueWorker(${this::class.simpleName}) failed processing queueId=$queueId" }
            tx.executeWithoutResult {
                val r = queue.findById(queueId).get()
                r.attempts += 1
                r.lastError = e.message?.take(2000)
                r.updatedAt = Instant.now()
                if (r.attempts >= maxAttempts) {
                    r.state = QueueState.FAILED
                    r.nextAttemptAt = null
                } else {
                    val backoff = min(
                        maxBackoffSeconds,
                        (baseBackoffSeconds.toDouble().pow(r.attempts.toDouble())).toLong(),
                    )
                    r.nextAttemptAt = Instant.now().plusSeconds(backoff)
                }
                queue.save(r)
            }
        }
    }
}
