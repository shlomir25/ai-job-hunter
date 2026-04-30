package com.jobhunter.ingestion.client

import com.jobhunter.ingestion.dto.RawEmail
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.mail.Folder
import jakarta.mail.Multipart
import jakarta.mail.Session
import jakarta.mail.Store
import jakarta.mail.internet.MimeMessage
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.Properties

private val log = KotlinLogging.logger {}

@Component
class JakartaMailImapClient : ImapClient {
    override fun fetch(
        host: String, port: Int, username: String, password: String,
        folder: String, fromFilter: String, maxMessages: Int,
    ): List<RawEmail> {
        val props = Properties().apply {
            setProperty("mail.store.protocol", if (port == 993) "imaps" else "imap")
            setProperty("mail.imap.host", host)
            setProperty("mail.imap.port", port.toString())
            setProperty("mail.imaps.host", host)
            setProperty("mail.imaps.port", port.toString())
        }
        val session = Session.getInstance(props)
        val store: Store = session.getStore(if (port == 993) "imaps" else "imap")
        store.connect(host, port, username, password)
        try {
            val mbox = store.getFolder(folder)
            mbox.open(Folder.READ_ONLY)
            try {
                // Server-side `FromStringTerm` is unreliable across IMAP implementations
                // (some don't index the From header for SEARCH). Fetch all messages and
                // filter client-side; this is fine for the per-source maxMessages cap we apply.
                val all = mbox.messages.takeLast(maxMessages)
                return all
                    .mapNotNull { msg -> toRawEmail(msg as MimeMessage) }
                    .filter { it.from.contains(fromFilter, ignoreCase = true) }
            } finally {
                mbox.close(false)
            }
        } finally {
            store.close()
        }
    }

    private fun toRawEmail(msg: MimeMessage): RawEmail? = try {
        val content = msg.content
        val (html, text) = when (content) {
            is String -> {
                val mime = msg.contentType.lowercase()
                if (mime.contains("html")) content to null else null to content
            }
            is Multipart -> extractFromMultipart(content)
            else -> null to null
        }
        RawEmail(
            messageId = msg.messageID ?: "${msg.from?.firstOrNull()}|${msg.sentDate?.time}",
            from = msg.from?.firstOrNull()?.toString() ?: "",
            subject = msg.subject ?: "",
            receivedAt = msg.receivedDate?.toInstant() ?: Instant.now(),
            htmlBody = html,
            textBody = text,
        )
    } catch (e: Exception) {
        log.warn(e) { "Failed to convert message to RawEmail" }
        null
    }

    private fun extractFromMultipart(mp: Multipart): Pair<String?, String?> {
        var html: String? = null
        var text: String? = null
        for (i in 0 until mp.count) {
            val part = mp.getBodyPart(i)
            val type = part.contentType.lowercase()
            when {
                type.contains("text/html") && html == null -> html = (part.content as? String)
                type.contains("text/plain") && text == null -> text = (part.content as? String)
                part.content is Multipart -> {
                    val (h, t) = extractFromMultipart(part.content as Multipart)
                    if (html == null) html = h
                    if (text == null) text = t
                }
            }
        }
        return html to text
    }
}
