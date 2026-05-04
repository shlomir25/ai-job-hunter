package com.jobhunter.matching.worker

import com.fasterxml.jackson.databind.ObjectMapper
import com.jobhunter.core.client.LlmClient
import com.jobhunter.core.domain.Match
import com.jobhunter.core.domain.MatchState
import com.jobhunter.core.domain.QueueState
import com.jobhunter.core.repository.CvRepository
import com.jobhunter.core.repository.JobPostingRepository
import com.jobhunter.core.repository.MatchRepository
import com.jobhunter.core.repository.PostingEmbeddingRepository
import com.jobhunter.core.repository.ProcessingQueueRepository
import com.jobhunter.core.worker.QueueWorker
import com.jobhunter.matching.config.MatchingProperties
import com.jobhunter.matching.dto.CvSummary
import com.jobhunter.matching.prompt.MatchPromptBuilder
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import java.time.Instant

@Component
class MatchWorker(
  private val queue: ProcessingQueueRepository,
  private val postings: JobPostingRepository,
  private val embeddings: PostingEmbeddingRepository,
  private val cvs: CvRepository,
  private val matches: MatchRepository,
  txManager: PlatformTransactionManager,
  private val llm: LlmClient,
  private val promptBuilder: MatchPromptBuilder,
  private val mapper: ObjectMapper,
  private val props: MatchingProperties,
) : QueueWorker(
  inputState = QueueState.EMBEDDED,
  outputState = QueueState.MATCHED,
  queue = queue,
  txManager = txManager,
  maxAttempts = 3,
) {
  override fun process(jobPostingId: Long) {
    val posting = postings.findById(jobPostingId).orElseThrow {
      IllegalStateException("Posting $jobPostingId not found")
    }
    val cv = cvs.findActive()
      ?: throw IllegalStateException("No active CV; upload one before matching can proceed")

    val cvVec = formatVector(cv.embedding)
    val cosine = embeddings.cosineToVector(jobPostingId, cvVec)
      ?: throw IllegalStateException("No embedding for posting $jobPostingId")

    if (cosine < props.cosineThreshold) {
      terminate(jobPostingId, QueueState.IRRELEVANT)
      return
    }

    val cvSummary: CvSummary = mapper.readValue(
      cv.structuredSummary ?: "{}",
      CvSummary::class.java,
    )
    val score = promptBuilder.invoke(llm, cvSummary, posting, mapper)

    if (score.score < props.llmScoreThreshold) {
      terminate(jobPostingId, QueueState.IRRELEVANT)
      return
    }

    matches.save(
      Match(
        jobPostingId = jobPostingId,
        cvId = cv.id!!,
        cosineSimilarity = cosine,
        llmScore = score.score,
        llmReasoning = mapper.writeValueAsString(score),
        state = MatchState.READY_FOR_REVIEW,
      ),
    )
  }

  private fun terminate(jobPostingId: Long, terminalState: QueueState) {
    val row = queue.findAll().first { it.jobPostingId == jobPostingId && it.state == QueueState.EMBEDDED }
    row.state = terminalState
    row.attempts = 0
    row.lastError = null
    row.nextAttemptAt = null
    row.updatedAt = Instant.now()
    queue.save(row)
  }

  private fun formatVector(arr: FloatArray): String =
    arr.joinToString(prefix = "[", postfix = "]") { it.toString() }
}
