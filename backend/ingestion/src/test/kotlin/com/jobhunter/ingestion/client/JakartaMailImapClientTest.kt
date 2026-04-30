package com.jobhunter.ingestion.client

import com.icegreen.greenmail.junit5.GreenMailExtension
import com.icegreen.greenmail.user.GreenMailUser
import com.icegreen.greenmail.util.GreenMailUtil
import com.icegreen.greenmail.util.ServerSetupTest
import jakarta.mail.Message
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JakartaMailImapClientTest {

    companion object {
        @RegisterExtension
        @JvmStatic
        val greenMail = GreenMailExtension(ServerSetupTest.SMTP_IMAP)
    }

    @Test
    fun `fetches html messages from inbox filtered by sender`() {
        val mailbox: GreenMailUser = greenMail.setUser("user@localhost", "user", "secret")

        // Deliver two emails directly to the IMAP store: one from LinkedIn, one noise
        deliverHtml(mailbox, from = "jobs-noreply@linkedin.com", subject = "New jobs", body = "<html><body><p>job</p></body></html>")
        deliverHtml(mailbox, from = "promo@spam.com", subject = "buy stuff", body = "<html><body>spam</body></html>")

        val client = JakartaMailImapClient()
        val emails = client.fetch(
            host = ServerSetupTest.IMAP.bindAddress,
            port = ServerSetupTest.IMAP.port,
            username = "user",
            password = "secret",
            folder = "INBOX",
            fromFilter = "linkedin.com",
            maxMessages = 50,
        )

        assertEquals(1, emails.size)
        val email = emails[0]
        assertTrue(email.from.contains("linkedin.com"))
        assertEquals("New jobs", email.subject)
        assertNotNull(email.htmlBody)
        assertTrue(email.htmlBody!!.contains("<p>job</p>"))
    }

    private fun deliverHtml(mailbox: GreenMailUser, from: String, subject: String, body: String) {
        val session = GreenMailUtil.getSession(ServerSetupTest.SMTP)
        val message = MimeMessage(session)
        message.setFrom(InternetAddress(from))
        message.setRecipients(Message.RecipientType.TO, "user@localhost")
        message.subject = subject
        message.setContent(body, "text/html; charset=UTF-8")
        mailbox.deliver(message)
    }
}
