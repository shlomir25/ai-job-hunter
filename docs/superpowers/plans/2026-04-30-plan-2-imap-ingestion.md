# Plan 2 — IMAP Ingestion

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Pull job-alert emails from Gmail (LinkedIn, Indeed, Glassdoor) via IMAP and turn each posting embedded in those emails into a `JobPosting` row in state `INGESTED`. Idempotent (rerunning the same emails does not duplicate rows). Triggered both on a 15-minute schedule and via an admin endpoint.

**Architecture:** New `backend/ingestion` module. `ImapClient` (Jakarta Mail) connects, fetches messages from a per-source filter, returns raw `RawEmail`s. `EmailParser` per provider extracts one or more postings from each email's HTML. `IngestionService` orchestrates per-source: fetch → parse → upsert posting → enqueue in `processing_queue` → update `JobSource.last_run_*`. Tests use **GreenMail** (in-process IMAP server) so no real Gmail is needed.

**Tech Stack additions:** Jakarta Mail (IMAP client), Jsoup (HTML extraction), GreenMail (test IMAP server).

**Reference spec:** `docs/superpowers/specs/2026-04-30-ai-job-hunter-design.md` §4.2 (ingestion module), §5.2 (push trigger), §6.2 (job_source), §6.3 (job_posting), §8.3 (scraper resilience — applies to ingestion sentinel checks too).

**Depends on:** Plan 1 (entities, repositories, `QueueNotifier`).

---

## File Structure

**`backend/ingestion/` (new module):**
- `build.gradle.kts` — depends on `core`; adds `jakarta.mail-api`, `org.eclipse.angus:angus-mail`, `org.jsoup:jsoup`
- `src/main/kotlin/com/jobhunter/ingestion/`
  - `dto/RawEmail.kt` — fetched email envelope + body
  - `dto/ParsedJobAlertEmail.kt` — list of postings extracted from one email
  - `dto/ParsedPostingDraft.kt` — one posting before persistence (no IDs)
  - `client/ImapClient.kt` — interface
  - `client/JakartaMailImapClient.kt` — JavaMail/Angus implementation
  - `parser/EmailParser.kt` — interface (`supports(senderDomain): Boolean`, `parse(email): ParsedJobAlertEmail`)
  - `parser/LinkedInAlertParser.kt`
  - `parser/IndeedAlertParser.kt`
  - `parser/GlassdoorAlertParser.kt`
  - `parser/EmailParserRegistry.kt` — picks the parser by sender domain
  - `service/IngestionService.kt` — orchestrator
  - `service/SourceConfigSeeder.kt` — `ApplicationRunner` that ensures the three IMAP sources exist
  - `scheduler/IngestionScheduler.kt` — `@Scheduled`
  - `controller/AdminIngestionController.kt`
  - `health/ImapHealthIndicator.kt`
  - `config/IngestionProperties.kt` — bound from `jobhunter.imap.*`
- `src/test/kotlin/com/jobhunter/ingestion/...`
  - `client/JakartaMailImapClientTest.kt` — uses GreenMail
  - `parser/LinkedInAlertParserTest.kt` — fixture-driven
  - `parser/IndeedAlertParserTest.kt` — fixture-driven
  - `parser/GlassdoorAlertParserTest.kt` — fixture-driven
  - `service/IngestionServiceTest.kt` — full slice with GreenMail + Testcontainers
  - `controller/AdminIngestionControllerTest.kt`
- `src/test/resources/email-fixtures/{linkedin,indeed,glassdoor}/sample-1.html`

**`backend/app/`** (modify):
- `build.gradle.kts` — add `implementation(project(":backend:ingestion"))`

**Root `settings.gradle.kts`** (modify):
- include `backend:ingestion`

---

## Task 1: Register the ingestion module

**Files:**
- Modify: `settings.gradle.kts`
- Create: `backend/ingestion/build.gradle.kts`
- Create: empty package directories

- [ ] **Step 1: Add `backend:ingestion` to `settings.gradle.kts`**

```kotlin
rootProject.name = "ai-job-hunter"

include("backend:core", "backend:ingestion", "backend:app")

project(":backend:core").projectDir = file("backend/core")
project(":backend:ingestion").projectDir = file("backend/ingestion")
project(":backend:app").projectDir = file("backend/app")
```

- [ ] **Step 2: Create `backend/ingestion/build.gradle.kts`**

```kotlin
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.4.1")
        mavenBom("org.springframework.ai:spring-ai-bom:1.0.0")
    }
}

dependencies {
    implementation(project(":backend:core"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    implementation("jakarta.mail:jakarta.mail-api:2.1.3")
    implementation("org.eclipse.angus:angus-mail:2.0.3")
    implementation("org.jsoup:jsoup:1.18.3")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(testFixtures(project(":backend:core")) ) {
        // testFixtures used so other modules can reuse AbstractRepositoryTest later if extracted
        // (no-op for now if no testFixtures jar configured; ignore if Gradle warns)
    }
    testImplementation("org.testcontainers:postgresql:1.20.4")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("com.icegreen:greenmail:2.1.3")
    testImplementation("com.icegreen:greenmail-junit5:2.1.3")
}

kotlin { jvmToolchain(21) }
```

- [ ] **Step 3: Create package directories**

```bash
mkdir -p backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/{dto,client,parser,service,scheduler,controller,health,config}
mkdir -p backend/ingestion/src/test/kotlin/com/jobhunter/ingestion/{client,parser,service,controller}
mkdir -p backend/ingestion/src/test/resources/email-fixtures/{linkedin,indeed,glassdoor}
```

- [ ] **Step 4: Wire ingestion into `backend/app/build.gradle.kts`**

Modify dependencies block:

```kotlin
dependencies {
    implementation(project(":backend:core"))
    implementation(project(":backend:ingestion"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:postgresql:1.20.4")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
}
```

- [ ] **Step 5: Verify the module builds**

```bash
./gradlew :backend:ingestion:compileKotlin :backend:app:compileKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts backend/ingestion/build.gradle.kts backend/app/build.gradle.kts
git add backend/ingestion/src
git commit -m "chore: bootstrap ingestion module"
```

---

## Task 2: `RawEmail` and `ParsedPostingDraft` DTOs

**Files:**
- Create: `backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/dto/RawEmail.kt`
- Create: `backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/dto/ParsedPostingDraft.kt`
- Create: `backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/dto/ParsedJobAlertEmail.kt`

These are pure data classes — no test needed for the types themselves. Tests for the parsers and clients that produce them follow.

- [ ] **Step 1: Create `RawEmail.kt`**

```kotlin
package com.jobhunter.ingestion.dto

import java.time.Instant

data class RawEmail(
    val messageId: String,
    val from: String,
    val subject: String,
    val receivedAt: Instant,
    val htmlBody: String?,
    val textBody: String?,
)
```

- [ ] **Step 2: Create `ParsedPostingDraft.kt`**

A draft posting, pre-persistence. Only fields the ingestion layer can fill from email content. The processing layer fills the rest later.

```kotlin
package com.jobhunter.ingestion.dto

import java.time.Instant

data class ParsedPostingDraft(
    val externalId: String,    // stable ID — typically a hash of source-url + title + company
    val sourceUrl: String?,
    val rawText: String,       // text content for downstream parsing
    val rawHtml: String?,
    val title: String?,        // pre-filled if email gives it cleanly; otherwise null
    val company: String?,
    val location: String?,
    val postedAt: Instant?,
)
```

- [ ] **Step 3: Create `ParsedJobAlertEmail.kt`**

```kotlin
package com.jobhunter.ingestion.dto

data class ParsedJobAlertEmail(
    val sourceEmail: RawEmail,
    val postings: List<ParsedPostingDraft>,
)
```

- [ ] **Step 4: Verify compile**

```bash
./gradlew :backend:ingestion:compileKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/dto
git commit -m "feat: add ingestion DTOs"
```

---

## Task 3: `ImapClient` interface + Jakarta Mail implementation

**Files:**
- Create: `backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/client/ImapClient.kt`
- Create: `backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/client/JakartaMailImapClient.kt`
- Create: `backend/ingestion/src/test/kotlin/com/jobhunter/ingestion/client/JakartaMailImapClientTest.kt`

- [ ] **Step 1: Write the failing test against GreenMail**

```kotlin
package com.jobhunter.ingestion.client

import com.icegreen.greenmail.junit5.GreenMailExtension
import com.icegreen.greenmail.util.GreenMailUtil
import com.icegreen.greenmail.util.ServerSetupTest
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.InternetAddress
import jakarta.mail.Message
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JakartaMailImapClientTest {

    companion object {
        @RegisterExtension
        @JvmStatic
        val greenMail = GreenMailExtension(ServerSetupTest.IMAP)
            .withConfiguration(com.icegreen.greenmail.configuration.GreenMailConfiguration.aConfig().withUser("user", "secret"))
    }

    @Test
    fun `fetches html messages from inbox filtered by sender`() {
        val session = GreenMailUtil.getSession(ServerSetupTest.IMAP)
        val inbox = greenMail.imap.createMailbox(greenMail.userManager.getUser("user").imapUser.then { it })
        // Send two emails: one from LinkedIn, one from a noisy sender
        sendHtml(from = "jobs-noreply@linkedin.com", subject = "New jobs", body = "<html><body><p>job</p></body></html>")
        sendHtml(from = "promo@spam.com", subject = "buy stuff", body = "<html><body>spam</body></html>")

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

    private fun sendHtml(from: String, subject: String, body: String) {
        val session = GreenMailUtil.getSession(ServerSetupTest.IMAP)
        val message = MimeMessage(session)
        message.setFrom(InternetAddress(from))
        message.setRecipients(Message.RecipientType.TO, "user@localhost")
        message.subject = subject
        message.setContent(body, "text/html; charset=UTF-8")
        com.icegreen.greenmail.util.GreenMailUtil.sendMimeMessage(message)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :backend:ingestion:test --tests "com.jobhunter.ingestion.client.JakartaMailImapClientTest"
```

Expected: FAIL — class does not exist.

- [ ] **Step 3: Define `ImapClient` interface**

```kotlin
package com.jobhunter.ingestion.client

import com.jobhunter.ingestion.dto.RawEmail

interface ImapClient {
    fun fetch(
        host: String,
        port: Int,
        username: String,
        password: String,
        folder: String,
        fromFilter: String,
        maxMessages: Int,
    ): List<RawEmail>
}
```

- [ ] **Step 4: Implement `JakartaMailImapClient`**

```kotlin
package com.jobhunter.ingestion.client

import com.jobhunter.ingestion.dto.RawEmail
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.mail.Folder
import jakarta.mail.Multipart
import jakarta.mail.Session
import jakarta.mail.Store
import jakarta.mail.internet.MimeMessage
import jakarta.mail.search.FromStringTerm
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
                val results = mbox.search(FromStringTerm(fromFilter))
                val sliced = results.takeLast(maxMessages)
                return sliced.mapNotNull { msg -> toRawEmail(msg as MimeMessage) }
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
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
./gradlew :backend:ingestion:test --tests "com.jobhunter.ingestion.client.JakartaMailImapClientTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/client \
        backend/ingestion/src/test/kotlin/com/jobhunter/ingestion/client
git commit -m "feat: add ImapClient with Jakarta Mail implementation"
```

---

## Task 4: `EmailParser` interface + LinkedIn alert parser + fixture test

**Files:**
- Create: `backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/parser/EmailParser.kt`
- Create: `backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/parser/LinkedInAlertParser.kt`
- Create: `backend/ingestion/src/test/resources/email-fixtures/linkedin/sample-1.html`
- Create: `backend/ingestion/src/test/kotlin/com/jobhunter/ingestion/parser/LinkedInAlertParserTest.kt`

LinkedIn job-alert emails have a recognizable structure: `<table>` rows wrapping each job, each with a job title link, company name, and location. The parser uses Jsoup to extract them.

- [ ] **Step 1: Create the LinkedIn fixture**

`backend/ingestion/src/test/resources/email-fixtures/linkedin/sample-1.html`:

```html
<!DOCTYPE html>
<html>
<body>
<table>
  <tr class="job-card">
    <td>
      <a href="https://www.linkedin.com/comm/jobs/view/3940000001/">Senior Backend Engineer</a>
      <div class="company">Acme Robotics</div>
      <div class="location">Tel Aviv, Israel</div>
    </td>
  </tr>
  <tr class="job-card">
    <td>
      <a href="https://www.linkedin.com/comm/jobs/view/3940000002/">Platform Engineer (Kotlin)</a>
      <div class="company">Beta Cloud</div>
      <div class="location">Remote — Israel</div>
    </td>
  </tr>
</table>
</body>
</html>
```

- [ ] **Step 2: Write the failing test**

```kotlin
package com.jobhunter.ingestion.parser

import com.jobhunter.ingestion.dto.RawEmail
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class LinkedInAlertParserTest {

    private val parser = LinkedInAlertParser()

    private fun loadFixture(name: String): String =
        javaClass.getResourceAsStream("/email-fixtures/linkedin/$name")!!
            .bufferedReader().readText()

    @Test
    fun `supports linkedin alert sender`() {
        assertEquals(true, parser.supports("jobs-noreply@linkedin.com"))
        assertEquals(true, parser.supports("jobs-listings@linkedin.com"))
        assertEquals(false, parser.supports("alerts@indeed.com"))
    }

    @Test
    fun `extracts two postings from sample fixture`() {
        val email = RawEmail(
            messageId = "<m1@linkedin.com>",
            from = "jobs-noreply@linkedin.com",
            subject = "Your job alerts",
            receivedAt = Instant.parse("2026-04-29T10:00:00Z"),
            htmlBody = loadFixture("sample-1.html"),
            textBody = null,
        )

        val parsed = parser.parse(email)
        assertEquals(2, parsed.postings.size)

        val first = parsed.postings[0]
        assertEquals("Senior Backend Engineer", first.title)
        assertEquals("Acme Robotics", first.company)
        assertEquals("Tel Aviv, Israel", first.location)
        assertEquals("https://www.linkedin.com/comm/jobs/view/3940000001/", first.sourceUrl)
        assertEquals("3940000001", first.externalId)
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

```bash
./gradlew :backend:ingestion:test --tests "com.jobhunter.ingestion.parser.LinkedInAlertParserTest"
```

Expected: FAIL — types do not exist.

- [ ] **Step 4: Define `EmailParser` interface**

```kotlin
package com.jobhunter.ingestion.parser

import com.jobhunter.ingestion.dto.ParsedJobAlertEmail
import com.jobhunter.ingestion.dto.RawEmail

interface EmailParser {
    val sourceCode: String
    fun supports(senderAddress: String): Boolean
    fun parse(email: RawEmail): ParsedJobAlertEmail
}
```

- [ ] **Step 5: Implement `LinkedInAlertParser`**

```kotlin
package com.jobhunter.ingestion.parser

import com.jobhunter.ingestion.dto.ParsedJobAlertEmail
import com.jobhunter.ingestion.dto.ParsedPostingDraft
import com.jobhunter.ingestion.dto.RawEmail
import org.jsoup.Jsoup
import org.springframework.stereotype.Component

@Component
class LinkedInAlertParser : EmailParser {
    override val sourceCode = "IMAP_LINKEDIN_ALERTS"

    override fun supports(senderAddress: String): Boolean =
        senderAddress.lowercase().contains("@linkedin.com")

    override fun parse(email: RawEmail): ParsedJobAlertEmail {
        val html = email.htmlBody ?: return ParsedJobAlertEmail(email, emptyList())
        val doc = Jsoup.parse(html)
        val cards = doc.select("tr.job-card")
        val postings = cards.mapNotNull { card ->
            val link = card.selectFirst("a") ?: return@mapNotNull null
            val href = link.attr("href").ifBlank { return@mapNotNull null }
            val externalId = JOB_ID_PATTERN.find(href)?.groupValues?.get(1) ?: href.hashCode().toString()
            ParsedPostingDraft(
                externalId = externalId,
                sourceUrl = href,
                rawText = card.text(),
                rawHtml = card.outerHtml(),
                title = link.text().ifBlank { null },
                company = card.selectFirst(".company")?.text()?.ifBlank { null },
                location = card.selectFirst(".location")?.text()?.ifBlank { null },
                postedAt = email.receivedAt,
            )
        }
        return ParsedJobAlertEmail(email, postings)
    }

    companion object {
        private val JOB_ID_PATTERN = Regex("/jobs/view/(\\d+)/?")
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

```bash
./gradlew :backend:ingestion:test --tests "com.jobhunter.ingestion.parser.LinkedInAlertParserTest"
```

Expected: PASS — both tests.

- [ ] **Step 7: Commit**

```bash
git add backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/parser/EmailParser.kt \
        backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/parser/LinkedInAlertParser.kt \
        backend/ingestion/src/test/resources/email-fixtures/linkedin/sample-1.html \
        backend/ingestion/src/test/kotlin/com/jobhunter/ingestion/parser/LinkedInAlertParserTest.kt
git commit -m "feat: add EmailParser interface and LinkedIn alert parser"
```

---

## Task 5: Indeed and Glassdoor alert parsers

**Files:**
- Create: `backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/parser/IndeedAlertParser.kt`
- Create: `backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/parser/GlassdoorAlertParser.kt`
- Create: `backend/ingestion/src/test/resources/email-fixtures/indeed/sample-1.html`
- Create: `backend/ingestion/src/test/resources/email-fixtures/glassdoor/sample-1.html`
- Create: `backend/ingestion/src/test/kotlin/com/jobhunter/ingestion/parser/IndeedAlertParserTest.kt`
- Create: `backend/ingestion/src/test/kotlin/com/jobhunter/ingestion/parser/GlassdoorAlertParserTest.kt`

Indeed and Glassdoor alert emails follow similar patterns. We use representative fixtures here; when the real format differs, swap in a fresh fixture and the test will fail before production breaks.

- [ ] **Step 1: Create Indeed fixture**

`backend/ingestion/src/test/resources/email-fixtures/indeed/sample-1.html`:

```html
<!DOCTYPE html>
<html>
<body>
<div class="result">
  <h2><a href="https://www.indeed.com/viewjob?jk=abc123">Backend Engineer (Kotlin)</a></h2>
  <span class="company">Acme</span>
  <span class="loc">Tel Aviv</span>
</div>
<div class="result">
  <h2><a href="https://www.indeed.com/viewjob?jk=def456">DevOps Engineer</a></h2>
  <span class="company">Beta</span>
  <span class="loc">Remote</span>
</div>
</body>
</html>
```

- [ ] **Step 2: Create Glassdoor fixture**

`backend/ingestion/src/test/resources/email-fixtures/glassdoor/sample-1.html`:

```html
<!DOCTYPE html>
<html>
<body>
<table class="job-list">
<tr><td>
  <a href="https://www.glassdoor.com/job-listing/JV_KO0,8_IL.9,15_IC2421325_KE16,32.htm?jl=987654" data-jobid="987654">Senior SRE</a>
  <div data-test="employer-name">Cloud Co</div>
  <div data-test="job-location">Israel</div>
</td></tr>
</table>
</body>
</html>
```

- [ ] **Step 3: Write Indeed parser test**

```kotlin
package com.jobhunter.ingestion.parser

import com.jobhunter.ingestion.dto.RawEmail
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class IndeedAlertParserTest {
    private val parser = IndeedAlertParser()

    private fun loadFixture(): String =
        javaClass.getResourceAsStream("/email-fixtures/indeed/sample-1.html")!!
            .bufferedReader().readText()

    @Test
    fun `supports indeed alert sender`() {
        assertEquals(true, parser.supports("alerts@indeed.com"))
        assertEquals(false, parser.supports("foo@example.com"))
    }

    @Test
    fun `extracts postings`() {
        val email = RawEmail(
            messageId = "<m@indeed.com>",
            from = "alerts@indeed.com",
            subject = "Jobs",
            receivedAt = Instant.now(),
            htmlBody = loadFixture(),
            textBody = null,
        )
        val parsed = parser.parse(email)
        assertEquals(2, parsed.postings.size)
        assertEquals("Backend Engineer (Kotlin)", parsed.postings[0].title)
        assertEquals("Acme", parsed.postings[0].company)
        assertEquals("abc123", parsed.postings[0].externalId)
    }
}
```

- [ ] **Step 4: Write Glassdoor parser test**

```kotlin
package com.jobhunter.ingestion.parser

import com.jobhunter.ingestion.dto.RawEmail
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class GlassdoorAlertParserTest {
    private val parser = GlassdoorAlertParser()

    private fun loadFixture(): String =
        javaClass.getResourceAsStream("/email-fixtures/glassdoor/sample-1.html")!!
            .bufferedReader().readText()

    @Test
    fun `supports glassdoor alert sender`() {
        assertEquals(true, parser.supports("noreply@glassdoor.com"))
        assertEquals(false, parser.supports("foo@example.com"))
    }

    @Test
    fun `extracts postings using data-jobid`() {
        val email = RawEmail(
            messageId = "<m@glassdoor.com>",
            from = "noreply@glassdoor.com",
            subject = "Daily jobs",
            receivedAt = Instant.now(),
            htmlBody = loadFixture(),
            textBody = null,
        )
        val parsed = parser.parse(email)
        assertEquals(1, parsed.postings.size)
        val p = parsed.postings[0]
        assertEquals("Senior SRE", p.title)
        assertEquals("Cloud Co", p.company)
        assertEquals("Israel", p.location)
        assertEquals("987654", p.externalId)
    }
}
```

- [ ] **Step 5: Run both tests to verify they fail**

```bash
./gradlew :backend:ingestion:test --tests "com.jobhunter.ingestion.parser.IndeedAlertParserTest" --tests "com.jobhunter.ingestion.parser.GlassdoorAlertParserTest"
```

Expected: FAIL.

- [ ] **Step 6: Implement `IndeedAlertParser`**

```kotlin
package com.jobhunter.ingestion.parser

import com.jobhunter.ingestion.dto.ParsedJobAlertEmail
import com.jobhunter.ingestion.dto.ParsedPostingDraft
import com.jobhunter.ingestion.dto.RawEmail
import org.jsoup.Jsoup
import org.springframework.stereotype.Component

@Component
class IndeedAlertParser : EmailParser {
    override val sourceCode = "IMAP_INDEED_ALERTS"

    override fun supports(senderAddress: String): Boolean =
        senderAddress.lowercase().contains("@indeed.com")

    override fun parse(email: RawEmail): ParsedJobAlertEmail {
        val html = email.htmlBody ?: return ParsedJobAlertEmail(email, emptyList())
        val doc = Jsoup.parse(html)
        val results = doc.select("div.result")
        val postings = results.mapNotNull { card ->
            val link = card.selectFirst("a") ?: return@mapNotNull null
            val href = link.attr("href").ifBlank { return@mapNotNull null }
            val jk = JK_PATTERN.find(href)?.groupValues?.get(1) ?: href.hashCode().toString()
            ParsedPostingDraft(
                externalId = jk,
                sourceUrl = href,
                rawText = card.text(),
                rawHtml = card.outerHtml(),
                title = link.text().ifBlank { null },
                company = card.selectFirst(".company")?.text()?.ifBlank { null },
                location = card.selectFirst(".loc")?.text()?.ifBlank { null },
                postedAt = email.receivedAt,
            )
        }
        return ParsedJobAlertEmail(email, postings)
    }

    companion object {
        private val JK_PATTERN = Regex("[?&]jk=([a-zA-Z0-9]+)")
    }
}
```

- [ ] **Step 7: Implement `GlassdoorAlertParser`**

```kotlin
package com.jobhunter.ingestion.parser

import com.jobhunter.ingestion.dto.ParsedJobAlertEmail
import com.jobhunter.ingestion.dto.ParsedPostingDraft
import com.jobhunter.ingestion.dto.RawEmail
import org.jsoup.Jsoup
import org.springframework.stereotype.Component

@Component
class GlassdoorAlertParser : EmailParser {
    override val sourceCode = "IMAP_GLASSDOOR_ALERTS"

    override fun supports(senderAddress: String): Boolean =
        senderAddress.lowercase().contains("@glassdoor.com")

    override fun parse(email: RawEmail): ParsedJobAlertEmail {
        val html = email.htmlBody ?: return ParsedJobAlertEmail(email, emptyList())
        val doc = Jsoup.parse(html)
        val anchors = doc.select("a[data-jobid]")
        val postings = anchors.mapNotNull { link ->
            val id = link.attr("data-jobid").ifBlank { return@mapNotNull null }
            val href = link.attr("href").ifBlank { null }
            val container = link.parent()
            ParsedPostingDraft(
                externalId = id,
                sourceUrl = href,
                rawText = container?.text() ?: link.text(),
                rawHtml = container?.outerHtml() ?: link.outerHtml(),
                title = link.text().ifBlank { null },
                company = container?.selectFirst("[data-test=employer-name]")?.text()?.ifBlank { null },
                location = container?.selectFirst("[data-test=job-location]")?.text()?.ifBlank { null },
                postedAt = email.receivedAt,
            )
        }
        return ParsedJobAlertEmail(email, postings)
    }
}
```

- [ ] **Step 8: Run the tests to verify they pass**

```bash
./gradlew :backend:ingestion:test --tests "com.jobhunter.ingestion.parser.IndeedAlertParserTest" --tests "com.jobhunter.ingestion.parser.GlassdoorAlertParserTest"
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/parser/IndeedAlertParser.kt \
        backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/parser/GlassdoorAlertParser.kt \
        backend/ingestion/src/test/resources/email-fixtures \
        backend/ingestion/src/test/kotlin/com/jobhunter/ingestion/parser/IndeedAlertParserTest.kt \
        backend/ingestion/src/test/kotlin/com/jobhunter/ingestion/parser/GlassdoorAlertParserTest.kt
git commit -m "feat: add Indeed and Glassdoor alert parsers"
```

---

## Task 6: `EmailParserRegistry` — pick parser by sender

**Files:**
- Create: `backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/parser/EmailParserRegistry.kt`
- Create: `backend/ingestion/src/test/kotlin/com/jobhunter/ingestion/parser/EmailParserRegistryTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.jobhunter.ingestion.parser

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EmailParserRegistryTest {

    private val linkedin = LinkedInAlertParser()
    private val indeed = IndeedAlertParser()
    private val glassdoor = GlassdoorAlertParser()
    private val registry = EmailParserRegistry(listOf(linkedin, indeed, glassdoor))

    @Test
    fun `picks parser whose supports returns true`() {
        assertEquals(linkedin, registry.parserFor("jobs-noreply@linkedin.com"))
        assertEquals(indeed, registry.parserFor("alerts@indeed.com"))
        assertEquals(glassdoor, registry.parserFor("noreply@glassdoor.com"))
    }

    @Test
    fun `returns null for unknown sender`() {
        assertNull(registry.parserFor("random@whatever.com"))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :backend:ingestion:test --tests "com.jobhunter.ingestion.parser.EmailParserRegistryTest"
```

Expected: FAIL.

- [ ] **Step 3: Implement `EmailParserRegistry`**

```kotlin
package com.jobhunter.ingestion.parser

import org.springframework.stereotype.Component

@Component
class EmailParserRegistry(private val parsers: List<EmailParser>) {
    fun parserFor(senderAddress: String): EmailParser? =
        parsers.firstOrNull { it.supports(senderAddress) }

    fun byCode(sourceCode: String): EmailParser? =
        parsers.firstOrNull { it.sourceCode == sourceCode }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :backend:ingestion:test --tests "com.jobhunter.ingestion.parser.EmailParserRegistryTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/parser/EmailParserRegistry.kt \
        backend/ingestion/src/test/kotlin/com/jobhunter/ingestion/parser/EmailParserRegistryTest.kt
git commit -m "feat: add EmailParserRegistry"
```

---

## Task 7: `IngestionProperties` and `SourceConfigSeeder`

**Files:**
- Create: `backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/config/IngestionProperties.kt`
- Create: `backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/service/SourceConfigSeeder.kt`
- Create: `backend/ingestion/src/test/kotlin/com/jobhunter/ingestion/service/SourceConfigSeederTest.kt`
- Modify: `backend/app/src/main/resources/application-local.yml.example`

`SourceConfigSeeder` is an `ApplicationRunner` that creates the three IMAP `JobSource` rows on startup if missing. The user fills IMAP credentials in `application-local.yml` (gitignored).

- [ ] **Step 1: Create `IngestionProperties`**

```kotlin
package com.jobhunter.ingestion.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("jobhunter.imap")
data class IngestionProperties(
    val host: String = "imap.gmail.com",
    val port: Int = 993,
    val username: String = "",
    val password: String = "",
    val maxMessagesPerRun: Int = 100,
)
```

Modify `JobHunterApplication.kt` to enable config props:

```kotlin
@SpringBootApplication
@ComponentScan(basePackages = ["com.jobhunter"])
@EntityScan("com.jobhunter.core.domain")
@EnableJpaRepositories("com.jobhunter.core.repository")
@EnableScheduling
@EnableAsync
@EnableRetry
@org.springframework.boot.context.properties.ConfigurationPropertiesScan("com.jobhunter")
class JobHunterApplication
```

- [ ] **Step 2: Write the failing seeder test**

```kotlin
package com.jobhunter.ingestion.service

import com.jobhunter.core.AbstractRepositoryTest
import com.jobhunter.core.repository.JobSourceRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourceConfigSeederTest : AbstractRepositoryTest() {
    @Autowired lateinit var sources: JobSourceRepository

    @Test
    fun `seed creates three IMAP sources when none exist`() {
        val seeder = SourceConfigSeeder(sources)
        seeder.run(/* ApplicationArguments unused */ null)
        val codes = sources.findAll().map { it.code }.toSet()
        assertTrue(codes.contains("IMAP_LINKEDIN_ALERTS"))
        assertTrue(codes.contains("IMAP_INDEED_ALERTS"))
        assertTrue(codes.contains("IMAP_GLASSDOOR_ALERTS"))
        assertEquals(3, codes.size)
    }

    @Test
    fun `seed is idempotent - running twice does not duplicate`() {
        val seeder = SourceConfigSeeder(sources)
        seeder.run(null)
        seeder.run(null)
        assertEquals(3L, sources.count())
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

```bash
./gradlew :backend:ingestion:test --tests "com.jobhunter.ingestion.service.SourceConfigSeederTest"
```

Expected: FAIL — `SourceConfigSeeder` does not exist.

- [ ] **Step 4: Implement `SourceConfigSeeder`**

```kotlin
package com.jobhunter.ingestion.service

import com.jobhunter.core.domain.JobSource
import com.jobhunter.core.domain.SourceType
import com.jobhunter.core.repository.JobSourceRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class SourceConfigSeeder(private val sources: JobSourceRepository) : ApplicationRunner {

    override fun run(args: ApplicationArguments?) {
        val seeds = listOf(
            "IMAP_LINKEDIN_ALERTS"  to """{"fromFilter":"@linkedin.com","folder":"INBOX"}""",
            "IMAP_INDEED_ALERTS"    to """{"fromFilter":"@indeed.com","folder":"INBOX"}""",
            "IMAP_GLASSDOOR_ALERTS" to """{"fromFilter":"@glassdoor.com","folder":"INBOX"}""",
        )
        for ((code, config) in seeds) {
            if (sources.findByCode(code) == null) {
                sources.save(JobSource(
                    code = code,
                    type = SourceType.IMAP,
                    enabled = true,
                    config = config,
                ))
                log.info { "Seeded JobSource $code" }
            }
        }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
./gradlew :backend:ingestion:test --tests "com.jobhunter.ingestion.service.SourceConfigSeederTest"
```

Expected: PASS.

- [ ] **Step 6: Update `application-local.yml.example` with IMAP block (already present from Plan 1, but make sure it matches)**

Confirm `backend/app/src/main/resources/application-local.yml.example` contains:

```yaml
jobhunter:
  imap:
    host: imap.gmail.com
    port: 993
    username: your.email@gmail.com
    password: your-google-app-password
    max-messages-per-run: 100
```

If not present, add it.

- [ ] **Step 7: Commit**

```bash
git add backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/config/IngestionProperties.kt \
        backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/service/SourceConfigSeeder.kt \
        backend/ingestion/src/test/kotlin/com/jobhunter/ingestion/service/SourceConfigSeederTest.kt \
        backend/app/src/main/kotlin/com/jobhunter/app/JobHunterApplication.kt \
        backend/app/src/main/resources/application-local.yml.example
git commit -m "feat: add IngestionProperties and SourceConfigSeeder"
```

---

## Task 8: `IngestionService` orchestrator + integration test

**Files:**
- Create: `backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/service/IngestionService.kt`
- Create: `backend/ingestion/src/test/kotlin/com/jobhunter/ingestion/service/IngestionServiceTest.kt`

The service ties everything together: read source config → fetch emails via IMAP → pick parser → parse → upsert postings → enqueue → update source health.

- [ ] **Step 1: Write the failing integration test**

```kotlin
package com.jobhunter.ingestion.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.icegreen.greenmail.junit5.GreenMailExtension
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
import jakarta.mail.Session
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
        val greenMail = GreenMailExtension(ServerSetupTest.IMAP)
            .withConfiguration(com.icegreen.greenmail.configuration.GreenMailConfiguration.aConfig().withUser("user", "secret"))
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

    @Test
    fun `ingests linkedin alert email and creates posting plus queue row`() {
        // 1) Seed the LinkedIn IMAP source
        val seeder = SourceConfigSeeder(sources)
        seeder.run(null)

        // 2) Send a LinkedIn-shaped email to GreenMail
        sendHtml(
            from = "jobs-noreply@linkedin.com",
            html = """
                <table>
                  <tr class="job-card"><td>
                    <a href="https://www.linkedin.com/comm/jobs/view/12345/">Backend Engineer</a>
                    <div class="company">Acme</div>
                    <div class="location">Tel Aviv</div>
                  </td></tr>
                </table>
            """.trimIndent(),
        )

        // 3) Run ingestion for the LinkedIn source
        val result = ingestionService.runSource("IMAP_LINKEDIN_ALERTS",
            host = ServerSetupTest.IMAP.bindAddress,
            port = ServerSetupTest.IMAP.port,
            username = "user", password = "secret",
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
        sendHtml(
            from = "jobs-noreply@linkedin.com",
            html = """<table><tr class="job-card"><td>
                <a href="https://www.linkedin.com/comm/jobs/view/77777/">x</a>
                <div class="company">y</div></td></tr></table>""",
        )

        ingestionService.runSource("IMAP_LINKEDIN_ALERTS",
            ServerSetupTest.IMAP.bindAddress, ServerSetupTest.IMAP.port,
            "user", "secret", 50)
        ingestionService.runSource("IMAP_LINKEDIN_ALERTS",
            ServerSetupTest.IMAP.bindAddress, ServerSetupTest.IMAP.port,
            "user", "secret", 50)

        assertEquals(1L, postings.count())
        assertEquals(1L, queue.count())
    }

    private fun sendHtml(from: String, html: String) {
        val session = Session.getInstance(java.util.Properties())
        val msg = MimeMessage(session)
        msg.setFrom(InternetAddress(from))
        msg.setRecipients(Message.RecipientType.TO, "user@localhost")
        msg.subject = "Job alerts"
        msg.setContent(html, "text/html; charset=UTF-8")
        com.icegreen.greenmail.util.GreenMailUtil.sendMimeMessage(msg)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :backend:ingestion:test --tests "com.jobhunter.ingestion.service.IngestionServiceTest"
```

Expected: FAIL — `IngestionService` does not exist.

- [ ] **Step 3: Implement `IngestionService`**

```kotlin
package com.jobhunter.ingestion.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.jobhunter.core.domain.JobPosting
import com.jobhunter.core.domain.ProcessingQueueRow
import com.jobhunter.core.domain.QueueState
import com.jobhunter.core.repository.JobPostingRepository
import com.jobhunter.core.repository.JobSourceRepository
import com.jobhunter.core.repository.ProcessingQueueRepository
import com.jobhunter.core.worker.QueueNotifier
import com.jobhunter.ingestion.client.ImapClient
import com.jobhunter.ingestion.dto.ParsedPostingDraft
import com.jobhunter.ingestion.parser.EmailParserRegistry
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

private val log = KotlinLogging.logger {}

data class IngestionRunResult(
    val emailsFetched: Int,
    val postingsCreated: Int,
)

@Service
class IngestionService(
    private val sources: JobSourceRepository,
    private val postings: JobPostingRepository,
    private val queue: ProcessingQueueRepository,
    private val imap: ImapClient,
    private val parsers: EmailParserRegistry,
    private val notifier: QueueNotifier,
    private val mapper: ObjectMapper,
) {

    @Transactional
    fun runSource(
        sourceCode: String,
        host: String, port: Int, username: String, password: String,
        maxMessages: Int,
    ): IngestionRunResult {
        val source = sources.findByCode(sourceCode)
            ?: error("Unknown source code: $sourceCode")
        val parser = parsers.byCode(sourceCode)
            ?: error("No parser registered for source $sourceCode")
        val cfg = mapper.readTree(source.config)
        val fromFilter = cfg["fromFilter"]?.asText()
            ?: error("Source $sourceCode missing fromFilter in config")
        val folder = cfg["folder"]?.asText() ?: "INBOX"

        var emailsFetched = 0
        var created = 0
        try {
            val emails = imap.fetch(host, port, username, password, folder, fromFilter, maxMessages)
            emailsFetched = emails.size
            for (email in emails) {
                val parsed = parser.parse(email)
                for (draft in parsed.postings) {
                    if (postings.findBySourceIdAndExternalId(source.id!!, draft.externalId) != null) {
                        continue
                    }
                    val saved = postings.save(toEntity(source.id!!, draft))
                    queue.save(ProcessingQueueRow(
                        jobPostingId = saved.id!!,
                        state = QueueState.INGESTED,
                    ))
                    created += 1
                }
            }
            source.lastRunAt = Instant.now()
            source.lastRunStatus = "OK"
            source.lastRunError = null
        } catch (e: Exception) {
            log.warn(e) { "Ingestion failed for $sourceCode" }
            source.lastRunAt = Instant.now()
            source.lastRunStatus = "FAILED"
            source.lastRunError = e.message?.take(2000)
            throw e
        } finally {
            sources.save(source)
        }
        if (created > 0) notifier.notify("queue_event")
        return IngestionRunResult(emailsFetched, created)
    }

    private fun toEntity(sourceId: Long, draft: ParsedPostingDraft): JobPosting = JobPosting(
        sourceId = sourceId,
        externalId = draft.externalId,
        rawText = draft.rawText,
        rawHtml = draft.rawHtml,
        sourceUrl = draft.sourceUrl,
        title = draft.title,
        company = draft.company,
        location = draft.location,
        postedAt = draft.postedAt,
        capturedAt = Instant.now(),
    )
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :backend:ingestion:test --tests "com.jobhunter.ingestion.service.IngestionServiceTest"
```

Expected: PASS — both tests.

- [ ] **Step 5: Commit**

```bash
git add backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/service/IngestionService.kt \
        backend/ingestion/src/test/kotlin/com/jobhunter/ingestion/service/IngestionServiceTest.kt
git commit -m "feat: add IngestionService orchestrator with idempotent upsert"
```

---

## Task 9: `IngestionScheduler` — periodic runs

**Files:**
- Create: `backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/scheduler/IngestionScheduler.kt`
- Create: `backend/ingestion/src/test/kotlin/com/jobhunter/ingestion/scheduler/IngestionSchedulerTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.jobhunter.ingestion.scheduler

import com.jobhunter.core.domain.JobSource
import com.jobhunter.core.domain.SourceType
import com.jobhunter.core.repository.JobSourceRepository
import com.jobhunter.ingestion.config.IngestionProperties
import com.jobhunter.ingestion.service.IngestionRunResult
import com.jobhunter.ingestion.service.IngestionService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class IngestionSchedulerTest {
    @Test
    fun `runs all enabled IMAP sources`() {
        val sources = mockk<JobSourceRepository>()
        val service = mockk<IngestionService>()
        val enabled = listOf(
            JobSource("IMAP_LINKEDIN_ALERTS", SourceType.IMAP, true, "{}", id = 1),
            JobSource("IMAP_INDEED_ALERTS",   SourceType.IMAP, true, "{}", id = 2),
        )
        val disabled = JobSource("IMAP_GLASSDOOR_ALERTS", SourceType.IMAP, false, "{}", id = 3)
        every { sources.findByEnabledTrue() } returns (enabled + disabled).filter { it.enabled }
        every { service.runSource(any(), any(), any(), any(), any(), any()) } returns IngestionRunResult(0, 0)

        val props = IngestionProperties(
            host = "imap.gmail.com", port = 993,
            username = "u", password = "p", maxMessagesPerRun = 50,
        )
        val scheduler = IngestionScheduler(sources, service, props)
        scheduler.run()

        verify { service.runSource("IMAP_LINKEDIN_ALERTS", "imap.gmail.com", 993, "u", "p", 50) }
        verify { service.runSource("IMAP_INDEED_ALERTS",   "imap.gmail.com", 993, "u", "p", 50) }
        verify(exactly = 0) { service.runSource("IMAP_GLASSDOOR_ALERTS", any(), any(), any(), any(), any()) }
    }

    @Test
    fun `continues when one source throws`() {
        val sources = mockk<JobSourceRepository>()
        val service = mockk<IngestionService>()
        every { sources.findByEnabledTrue() } returns listOf(
            JobSource("A", SourceType.IMAP, true, "{}", id = 1),
            JobSource("B", SourceType.IMAP, true, "{}", id = 2),
        )
        every { service.runSource("A", any(), any(), any(), any(), any()) } throws RuntimeException("bad")
        every { service.runSource("B", any(), any(), any(), any(), any()) } returns IngestionRunResult(0, 0)

        val props = IngestionProperties("h", 0, "u", "p", 1)
        IngestionScheduler(sources, service, props).run()

        verify { service.runSource("B", any(), any(), any(), any(), any()) }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :backend:ingestion:test --tests "com.jobhunter.ingestion.scheduler.IngestionSchedulerTest"
```

Expected: FAIL.

- [ ] **Step 3: Implement `IngestionScheduler`**

```kotlin
package com.jobhunter.ingestion.scheduler

import com.jobhunter.core.domain.SourceType
import com.jobhunter.core.repository.JobSourceRepository
import com.jobhunter.ingestion.config.IngestionProperties
import com.jobhunter.ingestion.service.IngestionService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class IngestionScheduler(
    private val sources: JobSourceRepository,
    private val service: IngestionService,
    private val props: IngestionProperties,
) {
    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT30S")
    fun run() {
        if (props.username.isBlank() || props.password.isBlank()) {
            log.info { "IMAP credentials not configured; skipping scheduled ingestion run" }
            return
        }
        for (src in sources.findByEnabledTrue().filter { it.type == SourceType.IMAP }) {
            try {
                val result = service.runSource(
                    sourceCode = src.code,
                    host = props.host, port = props.port,
                    username = props.username, password = props.password,
                    maxMessages = props.maxMessagesPerRun,
                )
                log.info { "Source ${src.code}: fetched ${result.emailsFetched} emails, created ${result.postingsCreated} postings" }
            } catch (e: Exception) {
                log.warn(e) { "Source ${src.code} run failed" }
            }
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :backend:ingestion:test --tests "com.jobhunter.ingestion.scheduler.IngestionSchedulerTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/scheduler/IngestionScheduler.kt \
        backend/ingestion/src/test/kotlin/com/jobhunter/ingestion/scheduler/IngestionSchedulerTest.kt
git commit -m "feat: add IngestionScheduler for periodic IMAP runs"
```

---

## Task 10: `AdminIngestionController` — manual trigger endpoint

**Files:**
- Create: `backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/controller/AdminIngestionController.kt`
- Create: `backend/ingestion/src/test/kotlin/com/jobhunter/ingestion/controller/AdminIngestionControllerTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.jobhunter.ingestion.controller

import com.jobhunter.ingestion.config.IngestionProperties
import com.jobhunter.ingestion.service.IngestionRunResult
import com.jobhunter.ingestion.service.IngestionService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class AdminIngestionControllerTest {

    private val service: IngestionService = mockk()
    private val props = IngestionProperties("h", 993, "u", "p", 50)
    private val controller = AdminIngestionController(service, props)
    private val mvc: MockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    @Test
    fun `POST run-now triggers source by code`() {
        every { service.runSource("IMAP_LINKEDIN_ALERTS", "h", 993, "u", "p", 50) } returns IngestionRunResult(2, 1)

        mvc.perform(post("/api/admin/ingestion/run-now")
            .param("source", "IMAP_LINKEDIN_ALERTS")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.emailsFetched").value(2))
            .andExpect(jsonPath("$.postingsCreated").value(1))
    }

    @Test
    fun `POST run-now returns 400 when source missing`() {
        mvc.perform(post("/api/admin/ingestion/run-now"))
            .andExpect(status().isBadRequest)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :backend:ingestion:test --tests "com.jobhunter.ingestion.controller.AdminIngestionControllerTest"
```

Expected: FAIL.

- [ ] **Step 3: Implement the controller**

```kotlin
package com.jobhunter.ingestion.controller

import com.jobhunter.ingestion.config.IngestionProperties
import com.jobhunter.ingestion.service.IngestionRunResult
import com.jobhunter.ingestion.service.IngestionService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/ingestion")
class AdminIngestionController(
    private val service: IngestionService,
    private val props: IngestionProperties,
) {
    @PostMapping("/run-now")
    fun runNow(@RequestParam source: String): IngestionRunResult =
        service.runSource(
            sourceCode = source,
            host = props.host, port = props.port,
            username = props.username, password = props.password,
            maxMessages = props.maxMessagesPerRun,
        )
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :backend:ingestion:test --tests "com.jobhunter.ingestion.controller.AdminIngestionControllerTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/controller \
        backend/ingestion/src/test/kotlin/com/jobhunter/ingestion/controller
git commit -m "feat: add admin endpoint to trigger ingestion run"
```

---

## Task 11: `ImapHealthIndicator`

**Files:**
- Create: `backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/health/ImapHealthIndicator.kt`
- Create: `backend/ingestion/src/test/kotlin/com/jobhunter/ingestion/health/ImapHealthIndicatorTest.kt`

Reports `UP` if the most recent IMAP run succeeded within 30 minutes, otherwise `DOWN`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.jobhunter.ingestion.health

import com.jobhunter.core.domain.JobSource
import com.jobhunter.core.domain.SourceType
import com.jobhunter.core.repository.JobSourceRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.boot.actuate.health.Status
import java.time.Instant
import kotlin.test.assertEquals

class ImapHealthIndicatorTest {

    @Test
    fun `UP when at least one IMAP source ran successfully within 30 min`() {
        val sources = mockk<JobSourceRepository>()
        every { sources.findByEnabledTrue() } returns listOf(
            JobSource("IMAP_LINKEDIN_ALERTS", SourceType.IMAP, true, "{}",
                lastRunAt = Instant.now().minusSeconds(60),
                lastRunStatus = "OK", id = 1),
        )
        val health = ImapHealthIndicator(sources).health()
        assertEquals(Status.UP, health.status)
    }

    @Test
    fun `DOWN when last successful run is older than 30 min`() {
        val sources = mockk<JobSourceRepository>()
        every { sources.findByEnabledTrue() } returns listOf(
            JobSource("IMAP_LINKEDIN_ALERTS", SourceType.IMAP, true, "{}",
                lastRunAt = Instant.now().minusSeconds(60 * 60),
                lastRunStatus = "OK", id = 1),
        )
        val health = ImapHealthIndicator(sources).health()
        assertEquals(Status.DOWN, health.status)
    }

    @Test
    fun `UNKNOWN when never run`() {
        val sources = mockk<JobSourceRepository>()
        every { sources.findByEnabledTrue() } returns listOf(
            JobSource("IMAP_LINKEDIN_ALERTS", SourceType.IMAP, true, "{}", id = 1),
        )
        val health = ImapHealthIndicator(sources).health()
        assertEquals(Status.UNKNOWN, health.status)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :backend:ingestion:test --tests "com.jobhunter.ingestion.health.ImapHealthIndicatorTest"
```

Expected: FAIL.

- [ ] **Step 3: Implement**

```kotlin
package com.jobhunter.ingestion.health

import com.jobhunter.core.domain.SourceType
import com.jobhunter.core.repository.JobSourceRepository
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.actuate.health.Status
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

@Component("imap")
class ImapHealthIndicator(private val sources: JobSourceRepository) : HealthIndicator {

    override fun health(): Health {
        val imapSources = sources.findByEnabledTrue().filter { it.type == SourceType.IMAP }
        val recentOk = imapSources.any { src ->
            src.lastRunStatus == "OK" &&
                src.lastRunAt?.let { Duration.between(it, Instant.now()) < Duration.ofMinutes(30) } == true
        }
        val anyAttempted = imapSources.any { it.lastRunAt != null }
        return when {
            recentOk -> Health.up().withDetail("imapSources", imapSources.size).build()
            !anyAttempted -> Health.status(Status.UNKNOWN)
                .withDetail("reason", "no IMAP runs yet").build()
            else -> Health.down()
                .withDetail("reason", "no successful IMAP run within 30 min").build()
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :backend:ingestion:test --tests "com.jobhunter.ingestion.health.ImapHealthIndicatorTest"
```

Expected: PASS — all three tests.

- [ ] **Step 5: Commit**

```bash
git add backend/ingestion/src/main/kotlin/com/jobhunter/ingestion/health \
        backend/ingestion/src/test/kotlin/com/jobhunter/ingestion/health
git commit -m "feat: add IMAP health indicator"
```

---

## Task 12: Final verification — full test suite + manual end-to-end

- [ ] **Step 1: Run the full test suite**

```bash
./gradlew test
```

Expected: PASS for all modules.

- [ ] **Step 2: Manual smoke test (optional, requires real Gmail or local SMTP)**

If you've set up a Gmail App Password and configured `application-local.yml`:

```bash
docker compose up -d postgres ollama
./gradlew :backend:app:bootRun
# in another terminal:
curl -X POST "http://localhost:8080/api/admin/ingestion/run-now?source=IMAP_LINKEDIN_ALERTS"
```

Expected: returns JSON with `emailsFetched` and `postingsCreated`. Postings appear in the `job_posting` table; `processing_queue` rows appear in state `INGESTED`.

- [ ] **Step 3: Update `README.md`** — add IMAP setup section

```markdown
## Setting up IMAP (Gmail)

1. Enable 2FA on your Gmail account.
2. Generate a Google App Password: https://myaccount.google.com/apppasswords
3. Copy `backend/app/src/main/resources/application-local.yml.example` to `application-local.yml`.
4. Fill in `jobhunter.imap.username` (your Gmail) and `jobhunter.imap.password` (the app password).
5. Apply Gmail filters/labels so your job-alert emails stay in INBOX (we filter by sender domain, e.g. `@linkedin.com`).
6. The scheduler runs every 15 minutes. Trigger immediately with `curl -X POST "http://localhost:8080/api/admin/ingestion/run-now?source=IMAP_LINKEDIN_ALERTS"`.
```

- [ ] **Step 4: Commit and tag**

```bash
git add README.md
git commit -m "docs: add IMAP setup instructions"
git tag plan-2-complete
```

---

## End of Plan 2

**At this point:**

- [x] LinkedIn, Indeed, Glassdoor IMAP sources auto-seeded on startup.
- [x] `IngestionService` fetches emails, parses them, upserts postings, enqueues `INGESTED` rows.
- [x] Idempotent — rerunning the same emails does not duplicate.
- [x] `@Scheduled` runs every 15 minutes; admin endpoint forces immediate run.
- [x] `ImapHealthIndicator` reflects latest run.
- [x] All tests pass (including GreenMail-backed integration test).

**Next plan: Plan 3 — Processing pipeline** (parse, classify, embed workers; postings flow `INGESTED → EMBEDDED`).
