package com.jobhunter.processing.controller

import com.jobhunter.core.domain.ProcessingQueueRow
import com.jobhunter.core.domain.QueueState
import com.jobhunter.core.repository.ProcessingQueueRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant

class AdminQueueControllerTest {

    private val queue: ProcessingQueueRepository = mockk()
    private val controller = AdminQueueController(queue)
    private val mvc: MockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    @Test
    fun `GET counts returns map of state to count`() {
        every { queue.countByState(QueueState.INGESTED) } returns 5
        every { queue.countByState(QueueState.PARSED) } returns 2
        every { queue.countByState(QueueState.CLASSIFIED) } returns 1
        every { queue.countByState(QueueState.EMBEDDED) } returns 0
        every { queue.countByState(QueueState.MATCHED) } returns 8
        every { queue.countByState(QueueState.IRRELEVANT) } returns 3
        every { queue.countByState(QueueState.OUT_OF_SCOPE) } returns 4
        every { queue.countByState(QueueState.FAILED) } returns 0

        mvc.perform(get("/api/admin/queue/counts"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.INGESTED").value(5))
            .andExpect(jsonPath("$.MATCHED").value(8))
    }

    @Test
    fun `POST requeue resets FAILED row`() {
        val row = ProcessingQueueRow(
            jobPostingId = 1, state = QueueState.FAILED, attempts = 3,
            lastError = "boom", id = 42, updatedAt = Instant.now(), createdAt = Instant.now(),
        )
        every { queue.findById(42) } returns java.util.Optional.of(row)
        every { queue.save(any()) } returns row

        mvc.perform(post("/api/admin/queue/42/requeue"))
            .andExpect(status().isOk)

        verify {
            queue.save(match<ProcessingQueueRow> {
                it.state == QueueState.INGESTED && it.attempts == 0 && it.lastError == null
            })
        }
    }
}
