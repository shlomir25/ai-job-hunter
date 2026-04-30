package com.jobhunter.processing.worker

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class WorkerSchedulerTest {

    @Test
    fun `tick runs all three workers in order`() {
        val parse = mockk<ParseWorker>(relaxed = true)
        val classify = mockk<ClassifyWorker>(relaxed = true)
        val embed = mockk<EmbedWorker>(relaxed = true)

        WorkerScheduler(parse, classify, embed).tick()

        verify { parse.runOnce(any()) }
        verify { classify.runOnce(any()) }
        verify { embed.runOnce(any()) }
    }

    @Test
    fun `failure in one worker does not stop the others`() {
        val parse = mockk<ParseWorker>()
        val classify = mockk<ClassifyWorker>(relaxed = true)
        val embed = mockk<EmbedWorker>(relaxed = true)

        every { parse.runOnce(any()) } throws RuntimeException("boom")

        WorkerScheduler(parse, classify, embed).tick()

        verify { classify.runOnce(any()) }
        verify { embed.runOnce(any()) }
    }
}
