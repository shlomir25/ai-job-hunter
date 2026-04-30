package com.jobhunter.processing.worker

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class WorkerScheduler(
    private val parseWorker: ParseWorker,
    private val classifyWorker: ClassifyWorker,
    private val embedWorker: EmbedWorker,
) {
    @Scheduled(fixedDelay = 60_000, initialDelay = 10_000)
    fun tick() {
        runSafe("parse")    { parseWorker.runOnce() }
        runSafe("classify") { classifyWorker.runOnce() }
        runSafe("embed")    { embedWorker.runOnce() }
    }

    private inline fun runSafe(name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            log.warn(e) { "Worker '$name' tick failed" }
        }
    }
}
