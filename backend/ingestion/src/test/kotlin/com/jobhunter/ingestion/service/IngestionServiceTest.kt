package com.jobhunter.ingestion.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.icegreen.greenmail.junit5.GreenMailExtension
import com.icegreen.greenmail.user.GreenMailUser
import com.icegreen.greenmail.util.GreenMailUtil
import com.icegreen.greenmail.util.ServerSetupTest
import com.jobhunter.core.AbstractRepositoryTest
import com.jobhunter.core.domain.QueueState
import com.jobhunter.core.repository.JobPostingRepository
import com.jobhunter.core.repository.JobSourceRepository
import com.jobhunter.core.repository.ProcessingQueueRepository
import com.jobhunter.core.worker.QueueNotifier
import com.jobhunter.ingestion.client.JakartaMailImapClient
import com.jobhunter.ingestion.parser.EmailParserRegistry
import com.jobhunter.ingestion.parser.GlassdoorAlertParser
import com.jobhunter.ingestion.parser.IndeedAlertParser
import com.jobhunter.ingestion.parser.LinkedInAlertParser
import jakarta.mail.Message
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ContextConfiguration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ContextConfiguration(classes = [IngestionServiceTest.TestBeans::class])
class IngestionServiceTest : AbstractRepositoryTest() {

    companion object {
        @RegisterExtension
        @JvmStatic
        val greenMail = GreenMailExtension(ServerSetupTest.SMTP_IMAP)
    }

    @TestConfiguration
    class TestBeans {
        @Bean fun objectMapper() = ObjectMapper()
        @Bean fun imapClient() = JakartaMailImapClient()
        @Bean fun linkedinParser() = LinkedInAlertParser()
        @Bean fun indeedParser() = IndeedAlertParser()
        @Bean fun glassdoorParser() = GlassdoorAlertParser()
        @Bean fun registry(parsers: List<com.jobhunter.ingestion.parser.EmailParser>) = EmailParserRegistry(parsers)
        @Bean fun queueNotifier(jdbc: JdbcTemplate) = QueueNotifier(jdbc)
        @Bean fun ingestionService(
            sources: JobSourceRepository,
            postings: JobPostingRepository,
            queue: ProcessingQueueRepository,
            client: JakartaMailImapClient,
            registry: EmailParserRegistry,
            notifier: QueueNotifier,
            mapper: ObjectMapper,
        ) = IngestionService(sources, postings, queue, client, registry, notifier, mapper)
    }

    @Autowired lateinit var sources: JobSourceRepository
    @Autowired lateinit var postings: JobPostingRepository
    @Autowired lateinit var queue: ProcessingQueueRepository
    @Autowired lateinit var ingestionService: IngestionService

    private fun setupUser(): GreenMailUser =
        greenMail.setUser("user@localhost", "user", "secret")

    private fun deliverHtml(mailbox: GreenMailUser, from: String, html: String) {
        val session = GreenMailUtil.getSession(ServerSetupTest.SMTP)
        val msg = MimeMessage(session)
        msg.setFrom(InternetAddress(from))
        msg.setRecipients(Message.RecipientType.TO, "user@localhost")
        msg.subject = "Job alerts"
        msg.setContent(html, "text/html; charset=UTF-8")
        mailbox.deliver(msg)
    }

    @Test
    fun `ingests linkedin alert email and creates posting plus queue row`() {
        // 1) Seed the IMAP sources
        SourceConfigSeeder(sources).run(null)

        // 2) Deliver a LinkedIn-shaped email to GreenMail
        val mailbox = setupUser()
        deliverHtml(mailbox, "jobs-noreply@linkedin.com", """
            <table>
              <tr class="job-card"><td>
                <a href="https://www.linkedin.com/comm/jobs/view/12345/">Backend Engineer</a>
                <div class="company">Acme</div>
                <div class="location">Tel Aviv</div>
              </td></tr>
            </table>
        """.trimIndent())

        // 3) Run ingestion for the LinkedIn source
        val result = ingestionService.runSource(
            sourceCode = "IMAP_LINKEDIN_ALERTS",
            host = ServerSetupTest.IMAP.bindAddress,
            port = ServerSetupTest.IMAP.port,
            username = "user",
            password = "secret",
            maxMessages = 50,
        )

        assertEquals(1, result.postingsCreated)
        assertEquals(1, postings.count().toInt())
        val saved = postings.findAll().first()
        assertEquals("12345", saved.externalId)

        // 4) processing_queue row in INGESTED state
        val rows = queue.findByState(QueueState.INGESTED)
        assertEquals(1, rows.size)
        assertEquals(saved.id, rows[0].jobPostingId)

        // 5) source health updated
        val src = sources.findByCode("IMAP_LINKEDIN_ALERTS")!!
        assertEquals("OK", src.lastRunStatus)
        assertNotNull(src.lastRunAt)
    }

    @Test
    fun `re-running same email does not duplicate posting`() {
        SourceConfigSeeder(sources).run(null)
        val mailbox = setupUser()
        deliverHtml(mailbox, "jobs-noreply@linkedin.com", """
            <table><tr class="job-card"><td>
                <a href="https://www.linkedin.com/comm/jobs/view/77777/">x</a>
                <div class="company">y</div></td></tr></table>
        """.trimIndent())

        ingestionService.runSource("IMAP_LINKEDIN_ALERTS",
            ServerSetupTest.IMAP.bindAddress, ServerSetupTest.IMAP.port,
            "user", "secret", 50)
        ingestionService.runSource("IMAP_LINKEDIN_ALERTS",
            ServerSetupTest.IMAP.bindAddress, ServerSetupTest.IMAP.port,
            "user", "secret", 50)

        assertEquals(1L, postings.count())
        assertEquals(1L, queue.count())
    }
}
