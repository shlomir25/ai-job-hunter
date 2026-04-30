package com.jobhunter.processing.controller

import com.jobhunter.core.domain.QueueState
import com.jobhunter.core.repository.ProcessingQueueRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/admin/queue")
class AdminQueueController(private val queue: ProcessingQueueRepository) {

  @GetMapping("/counts")
  fun counts(): Map<String, Long> =
    QueueState.entries.associate { it.name to queue.countByState(it) }

  @PostMapping("/{id}/requeue")
  fun requeue(@PathVariable id: Long) {
    val row = queue.findById(id).orElseThrow {
      IllegalArgumentException("Queue row $id not found")
    }
    row.state = QueueState.INGESTED // re-enter pipeline at the start
    row.attempts = 0
    row.lastError = null
    row.nextAttemptAt = null
    row.updatedAt = Instant.now()
    queue.save(row)
  }
}
