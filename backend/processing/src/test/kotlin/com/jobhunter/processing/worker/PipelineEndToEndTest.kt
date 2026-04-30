package com.jobhunter.processing.worker

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.jobhunter.core.AbstractRepositoryTest
import com.jobhunter.core.client.EmbeddingClient
import com.jobhunter.core.domain.*
import com.jobhunter.core.repository.*
import com.jobhunter.processing.client.RecordingLlmClient
import com.jobhunter.processing.config.ProcessingProperties
import com.jobhunter.processing.prompt.ClassifyPromptBuilder
import com.jobhunter.processing.prompt.ParsePromptBuilder
import com.jobhunter.processing.service.EmailExtractor
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.ContextConfiguration
import org.springframework.transaction.PlatformTransactionManager
import java.time.Instant
import kotlin.test.assertEquals

@ContextConfiguration(classes = [PipelineEndToEndTest.TestBeans::class])
class PipelineEndToEndTest : AbstractRepositoryTest() {

    @TestConfiguration
    class TestBeans {
        @Bean fun mapper() = ObjectMapper().registerKotlinModule()
        @Bean fun parseBuilder() = ParsePromptBuilder()
        @Bean fun classifyBuilder() = ClassifyPromptBuilder()
    }

    @Autowired lateinit var sources: JobSourceRepository
    @Autowired lateinit var postings: JobPostingRepository
    @Autowired lateinit var queue: ProcessingQueueRepository
    @Autowired lateinit var embeddings: PostingEmbeddingRepository
    @Autowired lateinit var txManager: PlatformTransactionManager
    @Autowired lateinit var mapper: ObjectMapper
    @Autowired lateinit var parseBuilder: ParsePromptBuilder
    @Autowired lateinit var classifyBuilder: ClassifyPromptBuilder

    @Test
    fun `posting flows from INGESTED to EMBEDDED`() {
        val raw = "Senior Backend Engineer at Acme. Tel Aviv. Apply: jobs@acme.com"
        val src = sources.save(JobSource("S", SourceType.IMAP, true, "{}"))
        val post = postings.save(JobPosting(
            sourceId = src.id!!, externalId = "E", rawText = raw, capturedAt = Instant.now(),
        ))
        val queueRow = queue.save(ProcessingQueueRow(jobPostingId = post.id!!, state = QueueState.INGESTED))

        val llm = RecordingLlmClient()
        // Both Parse and Classify use the same `user = raw` but different system prompts;
        // RecordingLlmClient.record(system, user, response) keys on the (system, user) pair so they're distinct.
        llm.record(parseBuilder.systemPrompt(), raw, """
            {"title":"Senior Backend Engineer","company":"Acme","location":"Tel Aviv",
             "isRemote":false,"language":"en","description":null,"requirements":null,
             "salaryText":null,"applyUrl":null,"contactEmail":"jobs@acme.com"}
        """.trimIndent())
        llm.record(classifyBuilder.systemPrompt(), raw, """["SOFTWARE_BACKEND"]""")

        val emailExtractor = EmailExtractor(llm)
        val embeddingClient = mockk<EmbeddingClient>()
        every { embeddingClient.embed(any()) } returns FloatArray(1024) { 0.1f }

        val parseWorker = ParseWorker(queue, postings, txManager, llm, parseBuilder, emailExtractor, mapper)
        val classifyWorker = ClassifyWorker(queue, postings, txManager, llm, classifyBuilder, mapper,
            ProcessingProperties(monitoredCategories = listOf(Category.SOFTWARE_BACKEND)))
        val embedWorker = EmbedWorker(queue, postings, embeddings, txManager, embeddingClient)

        parseWorker.runOnce()
        classifyWorker.runOnce()
        embedWorker.runOnce()

        val finalState = queue.findById(queueRow.id!!).get().state
        assertEquals(QueueState.EMBEDDED, finalState)

        val finalPost = postings.findById(post.id!!).get()
        assertEquals("Senior Backend Engineer", finalPost.title)
        assertEquals(listOf(Category.SOFTWARE_BACKEND), finalPost.categories)
        assertEquals("jobs@acme.com", finalPost.contactEmail)

        val emb = embeddings.findById(post.id!!).get()
        assertEquals(1024, emb.embedding.size)
    }
}
