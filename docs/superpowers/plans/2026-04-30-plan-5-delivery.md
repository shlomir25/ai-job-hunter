# Plan 5 — Delivery (Cover Letter Draft + SMTP Send)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When the user clicks "Send" on a `READY_FOR_REVIEW` match, generate a language-matched cover letter draft. The user reviews and possibly edits the draft, then confirms; the system then SMTP-sends the email with the active CV attached, writes an `email_send_record`, and transitions the match to `SENT` (or `SEND_FAILED` on error).

**Architecture:** New `backend/delivery` module. `CoverLetterPromptBuilder` produces English or Hebrew prompts based on `posting.language`. `DraftService` calls the LLM, fills `match.draft_subject` + `match.draft_body`, advances `match.state` to `DRAFTED`. `EmailValidator` runs regex + denylist against the `to_address`. `EmailSendService` composes a `MimeMessage` with the active CV as attachment, sends via Spring's `JavaMailSender`, writes `email_send_record` and updates `match.state` to `SENT` in one transaction.

**Tech Stack additions:** Spring's `JavaMailSender`; GreenMail SMTP for tests.

**Reference spec:** `docs/superpowers/specs/2026-04-30-ai-job-hunter-design.md` §4.2 (delivery module), §6.7 (match), §6.8 (email_send_record), §7.3 (cover letter prompt), §8.4 (send safety).

**Depends on:** Plans 1-4 (matches exist in `READY_FOR_REVIEW` state with an active CV).

---

## File Structure

**`backend/delivery/` (new module):**
- `build.gradle.kts` — depends on `core`; pulls `spring-boot-starter-mail`
- `src/main/kotlin/com/jobhunter/delivery/`
  - `dto/DraftedEmail.kt`
  - `dto/SendRequest.kt`
  - `service/CoverLetterService.kt` (the "DraftService")
  - `service/EmailValidator.kt`
  - `service/EmailSendService.kt`
  - `prompt/CoverLetterPromptBuilder.kt`
  - `controller/DeliveryController.kt`
  - `config/DeliveryProperties.kt`
- `src/test/kotlin/com/jobhunter/delivery/...`
  - `client/RecordingLlmClient.kt` — same shape as in Plans 3/4
  - `prompt/CoverLetterPromptBuilderTest.kt`
  - `service/EmailValidatorTest.kt`
  - `service/CoverLetterServiceTest.kt`
  - `service/EmailSendServiceTest.kt` (GreenMail SMTP)
  - `controller/DeliveryControllerTest.kt`
  - `EndToEndDeliveryTest.kt`
- `src/test/resources/prompts/cover-letter/{en,he}/sample-1-{input.json,expected.txt}`

**Root `settings.gradle.kts`** (modify): include `backend:delivery`.
**`backend/app/build.gradle.kts`** (modify): add dependency on delivery.

---

## Task 1: Bootstrap delivery module

**Files:**
- Modify: `settings.gradle.kts`
- Create: `backend/delivery/build.gradle.kts`
- Modify: `backend/app/build.gradle.kts`

- [ ] **Step 1: Update `settings.gradle.kts`**

```kotlin
rootProject.name = "ai-job-hunter"

include("backend:core", "backend:ingestion", "backend:processing", "backend:matching", "backend:delivery", "backend:app")

project(":backend:core").projectDir = file("backend/core")
project(":backend:ingestion").projectDir = file("backend/ingestion")
project(":backend:processing").projectDir = file("backend/processing")
project(":backend:matching").projectDir = file("backend/matching")
project(":backend:delivery").projectDir = file("backend/delivery")
project(":backend:app").projectDir = file("backend/app")
```

- [ ] **Step 2: Create `backend/delivery/build.gradle.kts`**

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
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:postgresql:1.20.4")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("com.icegreen:greenmail:2.1.3")
    testImplementation("com.icegreen:greenmail-junit5:2.1.3")
}

kotlin { jvmToolchain(21) }
```

- [ ] **Step 3: Wire into app**

Add `implementation(project(":backend:delivery"))` to `backend/app/build.gradle.kts`.

- [ ] **Step 4: Create directories**

```bash
mkdir -p backend/delivery/src/main/kotlin/com/jobhunter/delivery/{dto,service,prompt,controller,config}
mkdir -p backend/delivery/src/test/kotlin/com/jobhunter/delivery/{client,service,prompt,controller}
mkdir -p backend/delivery/src/test/resources/prompts/cover-letter/{en,he}
```

- [ ] **Step 5: Verify build**

```bash
./gradlew :backend:delivery:compileKotlin :backend:app:compileKotlin
```

- [ ] **Step 6: Copy `RecordingLlmClient`**

Same content as Plans 3/4. Place at `backend/delivery/src/test/kotlin/com/jobhunter/delivery/client/RecordingLlmClient.kt`:

```kotlin
package com.jobhunter.delivery.client

import com.jobhunter.core.client.LlmClient

class RecordingLlmClient : LlmClient {
    private val responses = mutableMapOf<String, String>()
    val callLog: MutableList<Pair<String, String>> = mutableListOf()

    fun record(system: String, user: String, response: String) {
        responses[key(system, user)] = response
    }

    fun recordByUser(user: String, response: String) {
        responses["USER::$user"] = response
    }

    override fun chat(system: String, user: String): String {
        callLog += system to user
        return responses[key(system, user)]
            ?: responses["USER::$user"]
            ?: error("No recorded response for user prompt:\n$user")
    }

    override fun chatStructured(system: String, user: String): String = chat(system, user)

    private fun key(system: String, user: String) = "${system.hashCode()}::${user.hashCode()}"
}
```

- [ ] **Step 7: Commit**

```bash
git add settings.gradle.kts backend/delivery backend/app/build.gradle.kts
git commit -m "chore: bootstrap delivery module"
```

---

## Task 2: `CoverLetterPromptBuilder` + golden tests

**Files:**
- Create: `backend/delivery/src/main/kotlin/com/jobhunter/delivery/dto/DraftedEmail.kt`
- Create: `backend/delivery/src/main/kotlin/com/jobhunter/delivery/prompt/CoverLetterPromptBuilder.kt`
- Create: fixtures under `backend/delivery/src/test/resources/prompts/cover-letter/{en,he}/`
- Create: `backend/delivery/src/test/kotlin/com/jobhunter/delivery/prompt/CoverLetterPromptBuilderTest.kt`

- [ ] **Step 1: Create `DraftedEmail`**

```kotlin
package com.jobhunter.delivery.dto

data class DraftedEmail(
    val subject: String,
    val body: String,
)
```

- [ ] **Step 2: Create English fixture**

`prompts/cover-letter/en/sample-1-input.json`:
```json
{
  "candidateName": "Shlomi Rahimi",
  "cvSummaryJson": "{\"skills\":[\"Kotlin\",\"Spring Boot\",\"Postgres\"],\"yearsTotalExperience\":7}",
  "postingTitle": "Backend Engineer (Kotlin)",
  "postingCompany": "Acme",
  "postingRequirements": "5+ years backend, Kotlin or Java, Postgres",
  "matchStrengthsJson": "[\"Kotlin\",\"Postgres\"]"
}
```

`prompts/cover-letter/en/sample-1-expected.txt`:
```
Hello,

I came across the Backend Engineer (Kotlin) role at Acme and wanted to reach out. My seven years of backend experience are squarely in your stack — Kotlin, Spring Boot, and Postgres are tools I work with daily. I've architected microservices platforms similar to what you describe, and I'd be happy to share specifics in a call.

Looking forward to hearing from you.
```

- [ ] **Step 3: Create Hebrew fixture**

`prompts/cover-letter/he/sample-1-input.json`:
```json
{
  "candidateName": "שלומי רחימי",
  "cvSummaryJson": "{\"skills\":[\"Kotlin\",\"Spring Boot\"],\"yearsTotalExperience\":7}",
  "postingTitle": "מהנדס Backend",
  "postingCompany": "אקמי",
  "postingRequirements": "5+ שנות ניסיון, Kotlin או Java",
  "matchStrengthsJson": "[\"Kotlin\"]"
}
```

`prompts/cover-letter/he/sample-1-expected.txt`:
```
שלום רב,

ראיתי את משרת מהנדס Backend באקמי ואשמח להציע מועמדות. שבע שנות ניסיון שלי ב-Kotlin ו-Spring Boot מתאימות מאוד לדרישות התפקיד. בנייתי מערכות מיקרו-שירותים בקנה מידה דומה, ואשמח להרחיב בשיחה.

אשמח לתגובתכם.
```

- [ ] **Step 4: Write the failing test**

```kotlin
package com.jobhunter.delivery.prompt

import com.jobhunter.core.domain.JobPosting
import com.jobhunter.delivery.client.RecordingLlmClient
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertTrue

class CoverLetterPromptBuilderTest {

    private val builder = CoverLetterPromptBuilder()

    private fun load(name: String): String =
        javaClass.getResourceAsStream("/prompts/cover-letter/$name")!!
            .bufferedReader().readText()

    private fun makePosting(language: String): JobPosting = JobPosting(
        sourceId = 1,
        externalId = "x",
        rawText = "raw",
        title = "Backend Engineer (Kotlin)",
        company = "Acme",
        requirements = "5+ years backend",
        language = language,
        capturedAt = Instant.now(),
    )

    @Test
    fun `english system prompt is in english`() {
        val sys = builder.systemPrompt("en")
        assertTrue(sys.contains("English"))
        assertTrue(!sys.contains("Hebrew"))
    }

    @Test
    fun `hebrew system prompt is in hebrew`() {
        val sys = builder.systemPrompt("he")
        assertTrue(sys.contains("Hebrew"))
    }

    @Test
    fun `english draft from recorded LLM`() {
        val expected = load("en/sample-1-expected.txt")
        val llm = RecordingLlmClient()
        val posting = makePosting("en")

        val cvSummaryJson = """{"skills":["Kotlin","Spring Boot","Postgres"],"yearsTotalExperience":7}"""
        val matchStrengths = listOf("Kotlin", "Postgres")
        val candidateName = "Shlomi Rahimi"

        val userPrompt = builder.userPrompt(candidateName, cvSummaryJson, posting, matchStrengths)
        llm.record(builder.systemPrompt("en"), userPrompt, expected.trim())

        val drafted = builder.invoke(llm, candidateName, cvSummaryJson, posting, matchStrengths)
        assertTrue(drafted.body.contains("Acme"))
        assertTrue(drafted.subject.contains("Backend Engineer"))
        assertTrue(drafted.subject.contains("Shlomi Rahimi"))
    }
}
```

- [ ] **Step 5: Run the test to verify it fails**

```bash
./gradlew :backend:delivery:test --tests "com.jobhunter.delivery.prompt.CoverLetterPromptBuilderTest"
```

Expected: FAIL.

- [ ] **Step 6: Implement `CoverLetterPromptBuilder`**

```kotlin
package com.jobhunter.delivery.prompt

import com.jobhunter.core.client.LlmClient
import com.jobhunter.core.domain.JobPosting
import com.jobhunter.delivery.dto.DraftedEmail
import org.springframework.stereotype.Component

@Component
class CoverLetterPromptBuilder {

    fun systemPrompt(language: String): String = when (language.lowercase()) {
        "he" -> """
            אתה כותב מכתב מקדים מקצועי בעברית, באורך של 4-6 משפטים.
            הזכר 2-3 התאמות ספציפיות בכישורים. טקסט רגיל בלבד, ללא נושא, ללא מצייני placeholder.
        """.trimIndent()
        else -> """
            You write a professional cover letter in English, 4-6 sentences.
            Mention 2-3 specific skill matches. Plain text only, no greeting placeholders, no subject line.
        """.trimIndent()
    }

    fun userPrompt(
        candidateName: String,
        cvSummaryJson: String,
        posting: JobPosting,
        strengths: List<String>,
    ): String = buildString {
        append("CV summary: ").append(cvSummaryJson).append("\n\n")
        append("Job:\n")
        append("Title: ").append(posting.title ?: "").append("\n")
        append("Company: ").append(posting.company ?: "").append("\n")
        append("Requirements: ").append(posting.requirements ?: "").append("\n\n")
        append("Top match strengths: ").append(strengths.joinToString(", ")).append("\n")
        append("Candidate name: ").append(candidateName).append("\n")
    }

    fun invoke(
        llm: LlmClient,
        candidateName: String,
        cvSummaryJson: String,
        posting: JobPosting,
        strengths: List<String>,
    ): DraftedEmail {
        val sys = systemPrompt(posting.language ?: "en")
        val user = userPrompt(candidateName, cvSummaryJson, posting, strengths)
        val body = llm.chat(sys, user).trim()
        val subject = subject(posting, candidateName)
        return DraftedEmail(subject = subject, body = body)
    }

    private fun subject(posting: JobPosting, candidateName: String): String {
        val title = posting.title ?: "Open role"
        return when ((posting.language ?: "en").lowercase()) {
            "he" -> "מועמדות לתפקיד $title — $candidateName"
            else -> "Application for $title — $candidateName"
        }
    }
}
```

- [ ] **Step 7: Run the test to verify it passes**

```bash
./gradlew :backend:delivery:test --tests "com.jobhunter.delivery.prompt.CoverLetterPromptBuilderTest"
```

Expected: PASS — all three tests.

- [ ] **Step 8: Commit**

```bash
git add backend/delivery/src/main/kotlin/com/jobhunter/delivery/dto/DraftedEmail.kt \
        backend/delivery/src/main/kotlin/com/jobhunter/delivery/prompt/CoverLetterPromptBuilder.kt \
        backend/delivery/src/test/resources/prompts/cover-letter \
        backend/delivery/src/test/kotlin/com/jobhunter/delivery/prompt/CoverLetterPromptBuilderTest.kt \
        backend/delivery/src/test/kotlin/com/jobhunter/delivery/client/RecordingLlmClient.kt
git commit -m "feat: add CoverLetterPromptBuilder with English and Hebrew system prompts"
```

---

## Task 3: `EmailValidator` + test

**Files:**
- Create: `backend/delivery/src/main/kotlin/com/jobhunter/delivery/config/DeliveryProperties.kt`
- Create: `backend/delivery/src/main/kotlin/com/jobhunter/delivery/service/EmailValidator.kt`
- Create: `backend/delivery/src/test/kotlin/com/jobhunter/delivery/service/EmailValidatorTest.kt`

- [ ] **Step 1: Create `DeliveryProperties`**

```kotlin
package com.jobhunter.delivery.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("jobhunter.delivery")
data class DeliveryProperties(
    val fromAddress: String = "",
    val candidateName: String = "Candidate",
    val denyList: List<String> = listOf(
        "noreply@",
        "no-reply@",
        "donotreply@",
        "do-not-reply@",
    ),
)
```

- [ ] **Step 2: Write the failing test**

```kotlin
package com.jobhunter.delivery.service

import com.jobhunter.delivery.config.DeliveryProperties
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class EmailValidatorTest {

    @Test
    fun `valid email passes`() {
        val v = EmailValidator(DeliveryProperties(fromAddress = "me@me.com"))
        assertEquals(true, v.isValid("jobs@acme.com"))
    }

    @Test
    fun `denies noreply addresses`() {
        val v = EmailValidator(DeliveryProperties(fromAddress = "me@me.com"))
        assertEquals(false, v.isValid("noreply@indeed.com"))
        assertEquals(false, v.isValid("DoNotReply@whatever.com"))
    }

    @Test
    fun `denies sending to self`() {
        val v = EmailValidator(DeliveryProperties(fromAddress = "me@me.com"))
        assertEquals(false, v.isValid("me@me.com"))
    }

    @Test
    fun `rejects malformed`() {
        val v = EmailValidator(DeliveryProperties(fromAddress = "me@me.com"))
        assertEquals(false, v.isValid("not-an-email"))
        assertEquals(false, v.isValid(""))
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

```bash
./gradlew :backend:delivery:test --tests "com.jobhunter.delivery.service.EmailValidatorTest"
```

Expected: FAIL.

- [ ] **Step 4: Implement `EmailValidator`**

```kotlin
package com.jobhunter.delivery.service

import com.jobhunter.delivery.config.DeliveryProperties
import org.springframework.stereotype.Component

@Component
class EmailValidator(private val props: DeliveryProperties) {

    fun isValid(toAddress: String): Boolean {
        if (!EMAIL_REGEX.matches(toAddress)) return false
        if (toAddress.equals(props.fromAddress, ignoreCase = true)) return false
        val lower = toAddress.lowercase()
        if (props.denyList.any { lower.contains(it.lowercase()) }) return false
        return true
    }

    companion object {
        private val EMAIL_REGEX = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
./gradlew :backend:delivery:test --tests "com.jobhunter.delivery.service.EmailValidatorTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/delivery/src/main/kotlin/com/jobhunter/delivery/config/DeliveryProperties.kt \
        backend/delivery/src/main/kotlin/com/jobhunter/delivery/service/EmailValidator.kt \
        backend/delivery/src/test/kotlin/com/jobhunter/delivery/service/EmailValidatorTest.kt
git commit -m "feat: add EmailValidator with regex, self-deny, and denylist"
```

---

## Task 4: `CoverLetterService` (drafting) + integration test

**Files:**
- Create: `backend/delivery/src/main/kotlin/com/jobhunter/delivery/service/CoverLetterService.kt`
- Create: `backend/delivery/src/test/kotlin/com/jobhunter/delivery/service/CoverLetterServiceTest.kt`

The service: load match → load posting → load active CV → build prompt → save draft on the match → advance state to `DRAFTED`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.jobhunter.delivery.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.jobhunter.core.AbstractRepositoryTest
import com.jobhunter.core.domain.*
import com.jobhunter.core.repository.*
import com.jobhunter.delivery.client.RecordingLlmClient
import com.jobhunter.delivery.config.DeliveryProperties
import com.jobhunter.delivery.prompt.CoverLetterPromptBuilder
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.ContextConfiguration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ContextConfiguration(classes = [CoverLetterServiceTest.TestBeans::class])
class CoverLetterServiceTest : AbstractRepositoryTest() {

    @TestConfiguration
    class TestBeans {
        @Bean fun mapper() = ObjectMapper().registerKotlinModule()
        @Bean fun coverLetterPromptBuilder() = CoverLetterPromptBuilder()
    }

    @Autowired lateinit var sources: JobSourceRepository
    @Autowired lateinit var postings: JobPostingRepository
    @Autowired lateinit var cvs: CvRepository
    @Autowired lateinit var matches: MatchRepository
    @Autowired lateinit var mapper: ObjectMapper
    @Autowired lateinit var promptBuilder: CoverLetterPromptBuilder

    @Test
    fun `drafts cover letter and advances match to DRAFTED`() {
        val src = sources.save(JobSource("S", SourceType.IMAP, true, "{}"))
        val post = postings.save(JobPosting(
            sourceId = src.id!!, externalId = "E", rawText = "x",
            title = "Backend Engineer", company = "Acme",
            requirements = "Kotlin", language = "en",
            capturedAt = Instant.now(),
        ))
        val cv = cvs.save(Cv("default", "cv.pdf", "application/pdf", byteArrayOf(0),
            "CV text", FloatArray(1024),
            structuredSummary = """{"skills":["Kotlin"],"yearsTotalExperience":7}""",
            isActive = true))
        val match = matches.save(Match(
            jobPostingId = post.id!!, cvId = cv.id!!,
            cosineSimilarity = 0.8, llmScore = 85,
            llmReasoning = """{"score":85,"strengths":["Kotlin"],"gaps":[],"summary":"good"}""",
            state = MatchState.READY_FOR_REVIEW,
        ))

        val llm = RecordingLlmClient()
        val sys = promptBuilder.systemPrompt("en")
        val userArg = promptBuilder.userPrompt(
            candidateName = "Shlomi",
            cvSummaryJson = cv.structuredSummary!!,
            posting = post,
            strengths = listOf("Kotlin"),
        )
        llm.record(sys, userArg, "I am applying for the role.")

        val service = CoverLetterService(matches, postings, cvs,
            DeliveryProperties(candidateName = "Shlomi"),
            promptBuilder, llm, mapper)

        val drafted = service.draft(match.id!!)
        assertNotNull(drafted)
        assertEquals("Application for Backend Engineer — Shlomi", drafted.subject)
        assertEquals("I am applying for the role.", drafted.body)

        val updated = matches.findById(match.id!!).get()
        assertEquals(MatchState.DRAFTED, updated.state)
        assertEquals("I am applying for the role.", updated.draftBody)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :backend:delivery:test --tests "com.jobhunter.delivery.service.CoverLetterServiceTest"
```

Expected: FAIL.

- [ ] **Step 3: Implement `CoverLetterService`**

```kotlin
package com.jobhunter.delivery.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.jobhunter.core.client.LlmClient
import com.jobhunter.core.domain.MatchState
import com.jobhunter.core.repository.CvRepository
import com.jobhunter.core.repository.JobPostingRepository
import com.jobhunter.core.repository.MatchRepository
import com.jobhunter.delivery.config.DeliveryProperties
import com.jobhunter.delivery.dto.DraftedEmail
import com.jobhunter.delivery.prompt.CoverLetterPromptBuilder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class CoverLetterService(
    private val matches: MatchRepository,
    private val postings: JobPostingRepository,
    private val cvs: CvRepository,
    private val props: DeliveryProperties,
    private val promptBuilder: CoverLetterPromptBuilder,
    private val llm: LlmClient,
    private val mapper: ObjectMapper,
) {

    @Transactional
    fun draft(matchId: Long): DraftedEmail {
        val match = matches.findById(matchId).orElseThrow {
            IllegalArgumentException("Match $matchId not found")
        }
        require(match.state == MatchState.READY_FOR_REVIEW || match.state == MatchState.DRAFTED) {
            "Cannot draft a match in state ${match.state}"
        }
        val posting = postings.findById(match.jobPostingId).orElseThrow {
            IllegalStateException("Posting ${match.jobPostingId} not found")
        }
        val cv = cvs.findById(match.cvId).orElseThrow {
            IllegalStateException("CV ${match.cvId} not found")
        }
        val strengths = extractStrengths(match.llmReasoning)
        val drafted = promptBuilder.invoke(
            llm = llm,
            candidateName = props.candidateName,
            cvSummaryJson = cv.structuredSummary ?: "{}",
            posting = posting,
            strengths = strengths,
        )

        match.draftSubject = drafted.subject
        match.draftBody = drafted.body
        match.state = MatchState.DRAFTED
        match.updatedAt = Instant.now()
        matches.save(match)

        return drafted
    }

    private fun extractStrengths(reasoningJson: String?): List<String> {
        if (reasoningJson.isNullOrBlank()) return emptyList()
        return try {
            val node: JsonNode = mapper.readTree(reasoningJson)
            node.get("strengths")?.let { arr ->
                arr.elements().asSequence().mapNotNull { it.asText().takeIf { v -> v.isNotBlank() } }.toList()
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :backend:delivery:test --tests "com.jobhunter.delivery.service.CoverLetterServiceTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/delivery/src/main/kotlin/com/jobhunter/delivery/service/CoverLetterService.kt \
        backend/delivery/src/test/kotlin/com/jobhunter/delivery/service/CoverLetterServiceTest.kt
git commit -m "feat: add CoverLetterService for drafting and DRAFTED transition"
```

---

## Task 5: `EmailSendService` + GreenMail SMTP integration test

**Files:**
- Create: `backend/delivery/src/main/kotlin/com/jobhunter/delivery/dto/SendRequest.kt`
- Create: `backend/delivery/src/main/kotlin/com/jobhunter/delivery/service/EmailSendService.kt`
- Create: `backend/delivery/src/test/kotlin/com/jobhunter/delivery/service/EmailSendServiceTest.kt`

- [ ] **Step 1: Create `SendRequest`**

```kotlin
package com.jobhunter.delivery.dto

data class SendRequest(
    val subject: String? = null,    // optional override of saved draft
    val body: String? = null,
)
```

- [ ] **Step 2: Write the failing test**

```kotlin
package com.jobhunter.delivery.service

import com.icegreen.greenmail.junit5.GreenMailExtension
import com.icegreen.greenmail.util.GreenMailUtil
import com.icegreen.greenmail.util.ServerSetupTest
import com.jobhunter.core.AbstractRepositoryTest
import com.jobhunter.core.domain.*
import com.jobhunter.core.repository.*
import com.jobhunter.delivery.config.DeliveryProperties
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.test.context.ContextConfiguration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ContextConfiguration(classes = [EmailSendServiceTest.TestBeans::class])
class EmailSendServiceTest : AbstractRepositoryTest() {

    companion object {
        @RegisterExtension
        @JvmStatic
        val greenMail = GreenMailExtension(ServerSetupTest.SMTP)
    }

    @TestConfiguration
    class TestBeans {
        @Bean fun mailSender(): org.springframework.mail.javamail.JavaMailSender = JavaMailSenderImpl().apply {
            host = ServerSetupTest.SMTP.bindAddress
            port = ServerSetupTest.SMTP.port
            // GreenMail SMTP needs no auth in default setup
        }
    }

    @Autowired lateinit var sources: JobSourceRepository
    @Autowired lateinit var postings: JobPostingRepository
    @Autowired lateinit var cvs: CvRepository
    @Autowired lateinit var matches: MatchRepository
    @Autowired lateinit var sends: EmailSendRecordRepository
    @Autowired lateinit var mailSender: org.springframework.mail.javamail.JavaMailSender

    @Test
    fun `sends email with attachment, writes record, transitions match to SENT`() {
        val src = sources.save(JobSource("S", SourceType.IMAP, true, "{}"))
        val post = postings.save(JobPosting(
            sourceId = src.id!!, externalId = "E", rawText = "x",
            title = "Backend Engineer", company = "Acme",
            contactEmail = "jobs@acme.com",
            capturedAt = Instant.now(),
        ))
        val cvBytes = "PDFBYTES".toByteArray()
        val cv = cvs.save(Cv("default", "shlomi.pdf", "application/pdf", cvBytes,
            "CV text", FloatArray(1024), null, isActive = true))
        val match = matches.save(Match(
            jobPostingId = post.id!!, cvId = cv.id!!,
            cosineSimilarity = 0.8, llmScore = 85, state = MatchState.DRAFTED,
            draftSubject = "Application for Backend Engineer",
            draftBody = "Hi, I'd like to apply.",
        ))

        val service = EmailSendService(
            matches = matches, postings = postings, cvs = cvs, sends = sends,
            mailSender = mailSender,
            validator = EmailValidator(DeliveryProperties(fromAddress = "me@example.com")),
            props = DeliveryProperties(fromAddress = "me@example.com", candidateName = "Shlomi"),
        )
        service.send(match.id!!, subjectOverride = null, bodyOverride = null)

        // Match in SENT state
        assertEquals(MatchState.SENT, matches.findById(match.id!!).get().state)
        // Send record written
        val record = sends.findByMatchId(match.id!!)
        assertNotNull(record)
        assertEquals("jobs@acme.com", record.toAddress)
        assertEquals("SENT", record.status)
        // GreenMail received the message
        val received = greenMail.receivedMessages
        assertEquals(1, received.size)
        assertEquals("Application for Backend Engineer", received[0].subject)
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

```bash
./gradlew :backend:delivery:test --tests "com.jobhunter.delivery.service.EmailSendServiceTest"
```

Expected: FAIL.

- [ ] **Step 4: Implement `EmailSendService`**

```kotlin
package com.jobhunter.delivery.service

import com.jobhunter.core.domain.EmailSendRecord
import com.jobhunter.core.domain.MatchState
import com.jobhunter.core.repository.CvRepository
import com.jobhunter.core.repository.EmailSendRecordRepository
import com.jobhunter.core.repository.JobPostingRepository
import com.jobhunter.core.repository.MatchRepository
import com.jobhunter.delivery.config.DeliveryProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.mail.internet.MimeMessage
import org.springframework.core.io.ByteArrayResource
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

private val log = KotlinLogging.logger {}

@Service
class EmailSendService(
    private val matches: MatchRepository,
    private val postings: JobPostingRepository,
    private val cvs: CvRepository,
    private val sends: EmailSendRecordRepository,
    private val mailSender: JavaMailSender,
    private val validator: EmailValidator,
    private val props: DeliveryProperties,
) {

    @Transactional
    fun send(matchId: Long, subjectOverride: String?, bodyOverride: String?) {
        val match = matches.findById(matchId).orElseThrow {
            IllegalArgumentException("Match $matchId not found")
        }
        // Idempotency — refuse to re-send a SENT match.
        if (match.state == MatchState.SENT) {
            throw IllegalStateException("Match $matchId already SENT")
        }
        require(match.state == MatchState.DRAFTED || match.state == MatchState.SEND_FAILED) {
            "Cannot send a match in state ${match.state}; draft it first"
        }

        val posting = postings.findById(match.jobPostingId).orElseThrow {
            IllegalStateException("Posting ${match.jobPostingId} not found")
        }
        val toAddress = posting.contactEmail
            ?: throw IllegalStateException("Posting ${posting.id} has no contact email; cannot send")

        if (!validator.isValid(toAddress)) {
            recordFailure(match, posting, "validation: address rejected")
            throw IllegalArgumentException("Address $toAddress failed validation")
        }

        val cv = cvs.findById(match.cvId).orElseThrow {
            IllegalStateException("CV ${match.cvId} not found")
        }

        val subject = subjectOverride ?: match.draftSubject
            ?: throw IllegalStateException("No subject; draft the match first")
        val body = bodyOverride ?: match.draftBody
            ?: throw IllegalStateException("No body; draft the match first")

        val mime: MimeMessage = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(mime, true, "UTF-8")
        helper.setFrom(props.fromAddress)
        helper.setTo(toAddress)
        helper.setSubject(subject)
        helper.setText(body, false)
        helper.addAttachment(cv.fileName, ByteArrayResource(cv.fileBytes))

        try {
            mailSender.send(mime)
            sends.save(EmailSendRecord(
                matchId = match.id!!,
                cvId = cv.id!!,
                toAddress = toAddress,
                subject = subject,
                body = body,
                attachmentFilename = cv.fileName,
                sentAt = Instant.now(),
                smtpMessageId = mime.messageID,
                status = "SENT",
            ))
            match.state = MatchState.SENT
            match.updatedAt = Instant.now()
            matches.save(match)
        } catch (e: Exception) {
            log.warn(e) { "SMTP send failed for match $matchId" }
            recordFailure(match, posting, e.message ?: "send failed")
            throw e
        }
    }

    private fun recordFailure(
        match: com.jobhunter.core.domain.Match,
        posting: com.jobhunter.core.domain.JobPosting,
        reason: String,
    ) {
        match.state = MatchState.SEND_FAILED
        match.updatedAt = Instant.now()
        matches.save(match)
        // Don't write email_send_record here — we only record successful sends per spec §6.8.
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
./gradlew :backend:delivery:test --tests "com.jobhunter.delivery.service.EmailSendServiceTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/delivery/src/main/kotlin/com/jobhunter/delivery/dto/SendRequest.kt \
        backend/delivery/src/main/kotlin/com/jobhunter/delivery/service/EmailSendService.kt \
        backend/delivery/src/test/kotlin/com/jobhunter/delivery/service/EmailSendServiceTest.kt
git commit -m "feat: add EmailSendService with SMTP send and audit"
```

---

## Task 6: `DeliveryController` + integration test

**Files:**
- Create: `backend/delivery/src/main/kotlin/com/jobhunter/delivery/controller/DeliveryController.kt`
- Create: `backend/delivery/src/test/kotlin/com/jobhunter/delivery/controller/DeliveryControllerTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.jobhunter.delivery.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.jobhunter.core.domain.Match
import com.jobhunter.core.domain.MatchState
import com.jobhunter.core.repository.MatchRepository
import com.jobhunter.delivery.dto.DraftedEmail
import com.jobhunter.delivery.service.CoverLetterService
import com.jobhunter.delivery.service.EmailSendService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant

class DeliveryControllerTest {

    private val draftService: CoverLetterService = mockk()
    private val sendService: EmailSendService = mockk(relaxed = true)
    private val matches: MatchRepository = mockk(relaxed = true)
    private val controller = DeliveryController(draftService, sendService, matches)
    private val mvc: MockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    private val mapper = ObjectMapper().registerKotlinModule()

    @Test
    fun `POST draft returns drafted email`() {
        every { draftService.draft(42) } returns DraftedEmail("subj", "body")
        mvc.perform(post("/api/matches/42/draft"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.subject").value("subj"))
            .andExpect(jsonPath("$.body").value("body"))
    }

    @Test
    fun `POST send delegates to sendService`() {
        mvc.perform(
            post("/api/matches/42/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf("subject" to "S", "body" to "B"))),
        ).andExpect(status().isOk)
        verify { sendService.send(42, "S", "B") }
    }

    @Test
    fun `POST skip transitions match to SKIPPED`() {
        val match = Match(
            jobPostingId = 1, cvId = 1, cosineSimilarity = 0.5, llmScore = 60,
            state = MatchState.READY_FOR_REVIEW, id = 7,
            createdAt = Instant.now(), updatedAt = Instant.now(),
        )
        every { matches.findById(7) } returns java.util.Optional.of(match)
        every { matches.save(any()) } answers { firstArg() }

        mvc.perform(post("/api/matches/7/skip")).andExpect(status().isOk)

        verify {
            matches.save(io.mockk.match<Match> { it.state == MatchState.SKIPPED })
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :backend:delivery:test --tests "com.jobhunter.delivery.controller.DeliveryControllerTest"
```

Expected: FAIL.

- [ ] **Step 3: Implement `DeliveryController`**

```kotlin
package com.jobhunter.delivery.controller

import com.jobhunter.core.domain.MatchState
import com.jobhunter.core.repository.MatchRepository
import com.jobhunter.delivery.dto.DraftedEmail
import com.jobhunter.delivery.dto.SendRequest
import com.jobhunter.delivery.service.CoverLetterService
import com.jobhunter.delivery.service.EmailSendService
import org.springframework.web.bind.annotation.*
import java.time.Instant

@RestController
@RequestMapping("/api/matches")
class DeliveryController(
    private val draftService: CoverLetterService,
    private val sendService: EmailSendService,
    private val matches: MatchRepository,
) {
    @PostMapping("/{id}/draft")
    fun draft(@PathVariable id: Long): DraftedEmail = draftService.draft(id)

    @PostMapping("/{id}/send")
    fun send(@PathVariable id: Long, @RequestBody request: SendRequest) {
        sendService.send(id, subjectOverride = request.subject, bodyOverride = request.body)
    }

    @PostMapping("/{id}/skip")
    fun skip(@PathVariable id: Long) {
        val match = matches.findById(id).orElseThrow {
            IllegalArgumentException("Match $id not found")
        }
        match.state = MatchState.SKIPPED
        match.updatedAt = Instant.now()
        matches.save(match)
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :backend:delivery:test --tests "com.jobhunter.delivery.controller.DeliveryControllerTest"
```

Expected: PASS — all three tests.

- [ ] **Step 5: Commit**

```bash
git add backend/delivery/src/main/kotlin/com/jobhunter/delivery/controller/DeliveryController.kt \
        backend/delivery/src/test/kotlin/com/jobhunter/delivery/controller/DeliveryControllerTest.kt
git commit -m "feat: add DeliveryController for draft, send, skip"
```

---

## Task 7: Final verification

- [ ] **Step 1: Run full test suite**

```bash
./gradlew test
```

Expected: ALL tests pass across all five backend modules.

- [ ] **Step 2: Update README**

Add:

```markdown
## Sending CVs

The send flow is intentionally two-step:

1. `POST /api/matches/{id}/draft` — generates a cover letter (in the posting's language). Returns subject + body.
2. `POST /api/matches/{id}/send` — body `{"subject": "...", "body": "..."}` (optional overrides; otherwise uses the saved draft). Sends via SMTP and writes `email_send_record`.
3. `POST /api/matches/{id}/skip` — dismiss without sending.

There is **no auto-retry on send failure** by design (per spec §8.4). On failure, the match transitions to `SEND_FAILED`; you decide whether to retry, edit, or skip.

Configure SMTP in `application-local.yml`:
```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: your.email@gmail.com
    password: your-google-app-password
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true

jobhunter:
  delivery:
    from-address: your.email@gmail.com
    candidate-name: Your Name
```
```

- [ ] **Step 3: Commit and tag**

```bash
git add README.md
git commit -m "docs: add send-flow instructions"
git tag plan-5-complete
```

---

## End of Plan 5

**At this point — the entire backend is functional end-to-end:**

- [x] CV upload → parse → embed → set active.
- [x] Job alert email arrives → ingestion → parse → classify → embed → match → `READY_FOR_REVIEW`.
- [x] User triggers `/draft` → cover letter generated in posting's language → match in `DRAFTED`.
- [x] User confirms `/send` → SMTP send with attachment → audit record → match in `SENT`.
- [x] All transitions are crash-safe (queue resumes after restart).
- [x] No auto-retry on send (intentional safety).
- [x] All tests pass.

**Next plan: Plan 6 — Frontend + Israeli scrapers** (React UI for the review queue + AllJobs and JobMaster scrapers).
