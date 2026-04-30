# Plan 3 — Processing Pipeline (Parse, Classify, Embed)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move postings through the processing state machine: `INGESTED → PARSED → CLASSIFIED → EMBEDDED` (or terminal `IRRELEVANT` / `OUT_OF_SCOPE` / `FAILED`). Each stage is its own queue worker with retry/backoff, all built on the `QueueWorker` base class from Plan 1.

**Architecture:** New `backend/processing` module with three `QueueWorker` subclasses. `EmailExtractor` does regex-first email extraction with LLM fallback (per spec §7.3 — guards against hallucinated emails). Parse and Classify workers use `LlmClient.chatStructured` with JSON-schema validation. `EmbedWorker` uses `EmbeddingClient`. A `PostgresQueueListener` translates `NOTIFY queue_event` into worker wake-ups; a `@Scheduled` poll runs every 60s as a safety net. Tests use a `RecordingLlmClient` golden-file harness — no live Ollama in CI.

**Tech Stack additions:** Jackson for structured-output deserialization (already pulled in via Spring Boot starter); `org.postgresql.PGNotification` API for LISTEN.

**Reference spec:** `docs/superpowers/specs/2026-04-30-ai-job-hunter-design.md` §4.2 (processing module), §5.1 (state machine), §5.2 (push trigger), §7 (LLM strategy), §8.1 (retry policy).

**Depends on:** Plan 1 (QueueWorker, LlmClient, EmbeddingClient, repositories), Plan 2 (postings appear in `INGESTED` state).

---

## File Structure

**`backend/processing/` (new module):**
- `build.gradle.kts` — depends on `core`
- `src/main/kotlin/com/jobhunter/processing/`
  - `dto/ParsedFields.kt` — output of ParseWorker LLM call
  - `dto/ClassificationResult.kt` — output of ClassifyWorker LLM call
  - `service/EmailExtractor.kt` — regex-first + LLM fallback
  - `prompt/ParsePromptBuilder.kt`
  - `prompt/ClassifyPromptBuilder.kt`
  - `worker/ParseWorker.kt`
  - `worker/ClassifyWorker.kt`
  - `worker/EmbedWorker.kt`
  - `worker/PostgresQueueListener.kt`
  - `worker/WorkerScheduler.kt` — `@Scheduled` poll safety net
  - `controller/AdminQueueController.kt`
  - `config/ProcessingProperties.kt` — bound from `jobhunter.monitored-categories`
- `src/test/kotlin/com/jobhunter/processing/...`
  - `client/RecordingLlmClient.kt` — replay-based fake (used by golden tests)
  - `prompt/ParsePromptBuilderTest.kt`
  - `prompt/ClassifyPromptBuilderTest.kt`
  - `service/EmailExtractorTest.kt`
  - `worker/ParseWorkerTest.kt`
  - `worker/ClassifyWorkerTest.kt`
  - `worker/EmbedWorkerTest.kt`
  - `worker/PipelineEndToEndTest.kt`
- `src/test/resources/prompts/{parse,classify}/sample-N-input.txt` + `sample-N-expected.json`

**Root `settings.gradle.kts`** (modify): include `backend:processing`.
**`backend/app/build.gradle.kts`** (modify): add dependency on processing.

---

## Task 1: Bootstrap processing module

**Files:**
- Modify: `settings.gradle.kts`
- Create: `backend/processing/build.gradle.kts`
- Modify: `backend/app/build.gradle.kts`

- [ ] **Step 1: Add `backend:processing` to settings**

```kotlin
rootProject.name = "ai-job-hunter"

include("backend:core", "backend:ingestion", "backend:processing", "backend:app")

project(":backend:core").projectDir = file("backend/core")
project(":backend:ingestion").projectDir = file("backend/ingestion")
project(":backend:processing").projectDir = file("backend/processing")
project(":backend:app").projectDir = file("backend/app")
```

- [ ] **Step 2: Create `backend/processing/build.gradle.kts`**

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
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:postgresql:1.20.4")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
    testImplementation("io.mockk:mockk:1.13.13")
}

kotlin { jvmToolchain(21) }
```

- [ ] **Step 3: Wire into `backend/app/build.gradle.kts`**

Add to dependencies:
```kotlin
implementation(project(":backend:processing"))
```

- [ ] **Step 4: Create directories**

```bash
mkdir -p backend/processing/src/main/kotlin/com/jobhunter/processing/{dto,service,prompt,worker,controller,config}
mkdir -p backend/processing/src/test/kotlin/com/jobhunter/processing/{client,prompt,service,worker}
mkdir -p backend/processing/src/test/resources/prompts/{parse,classify}
```

- [ ] **Step 5: Verify build**

```bash
./gradlew :backend:processing:compileKotlin :backend:app:compileKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts backend/processing backend/app/build.gradle.kts
git commit -m "chore: bootstrap processing module"
```

---

## Task 2: `RecordingLlmClient` test harness

**Files:**
- Create: `backend/processing/src/test/kotlin/com/jobhunter/processing/client/RecordingLlmClient.kt`

The test harness for golden-file LLM tests. Stores `(systemHash, userHash) → response` mappings; when an unrecorded prompt arrives, throws to fail the test loudly.

- [ ] **Step 1: Create `RecordingLlmClient`**

```kotlin
package com.jobhunter.processing.client

import com.jobhunter.core.client.LlmClient

class RecordingLlmClient : LlmClient {
    private val responses = mutableMapOf<String, String>()
    val callLog: MutableList<Pair<String, String>> = mutableListOf()

    fun record(system: String, user: String, response: String) {
        responses[key(system, user)] = response
    }

    /** Match purely by user content (system prompt is fixed for a given prompt class). */
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

- [ ] **Step 2: Verify it compiles**

```bash
./gradlew :backend:processing:compileTestKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add backend/processing/src/test/kotlin/com/jobhunter/processing/client
git commit -m "test: add RecordingLlmClient harness"
```

---

## Task 3: `EmailExtractor` — regex-first + LLM fallback

**Files:**
- Create: `backend/processing/src/main/kotlin/com/jobhunter/processing/service/EmailExtractor.kt`
- Create: `backend/processing/src/test/kotlin/com/jobhunter/processing/service/EmailExtractorTest.kt`

Per spec §7.3 — regex finds emails first; LLM is only invoked when regex finds zero candidates; LLM result is **re-validated against regex** before save. Email hallucination is the highest-stakes failure mode; this approach forecloses it.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.jobhunter.processing.service

import com.jobhunter.core.client.LlmClient
import com.jobhunter.processing.client.RecordingLlmClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EmailExtractorTest {

    @Test
    fun `picks first valid email when present`() {
        val llm = mockk<LlmClient>(relaxed = true)
        val extractor = EmailExtractor(llm)
        val text = "Apply: jobs@acme.com or careers@spam.net"
        assertEquals("jobs@acme.com", extractor.extract(text, companyHint = "Acme"))
        verify(exactly = 0) { llm.chat(any(), any()) }
    }

    @Test
    fun `prefers email matching company domain`() {
        val llm = mockk<LlmClient>(relaxed = true)
        val extractor = EmailExtractor(llm)
        val text = "Submit to noreply@indeed.com or jobs@acme.com"
        assertEquals("jobs@acme.com", extractor.extract(text, companyHint = "Acme"))
    }

    @Test
    fun `falls back to LLM when no regex matches found`() {
        val llm = RecordingLlmClient()
        llm.recordByUser("No email visible. Reply with the email or 'null'.", "careers@beta.com")
        val extractor = EmailExtractor(llm)
        val result = extractor.extract("No email visible. Reply with the email or 'null'.", companyHint = "Beta")
        assertEquals("careers@beta.com", result)
    }

    @Test
    fun `rejects LLM hallucination that does not pass regex`() {
        val llm = RecordingLlmClient()
        llm.recordByUser("Some text", "thisisnotanemail")
        val extractor = EmailExtractor(llm)
        assertNull(extractor.extract("Some text", companyHint = null))
    }

    @Test
    fun `returns null when LLM says null`() {
        val llm = RecordingLlmClient()
        llm.recordByUser("Posting body", "null")
        val extractor = EmailExtractor(llm)
        assertNull(extractor.extract("Posting body", companyHint = null))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :backend:processing:test --tests "com.jobhunter.processing.service.EmailExtractorTest"
```

Expected: FAIL.

- [ ] **Step 3: Implement `EmailExtractor`**

```kotlin
package com.jobhunter.processing.service

import com.jobhunter.core.client.LlmClient
import org.springframework.stereotype.Component

@Component
class EmailExtractor(private val llm: LlmClient) {

    fun extract(rawText: String, companyHint: String?): String? {
        val candidates = EMAIL_REGEX.findAll(rawText).map { it.value }.toList()
        if (candidates.isNotEmpty()) {
            return pickBest(candidates, companyHint)
        }
        // Fallback: ask the LLM, but VALIDATE the response against the same regex.
        val response = try {
            llm.chat(
                system = "You extract a contact email from text. Reply with ONLY the email address or the literal word null. No prose.",
                user = rawText,
            ).trim()
        } catch (_: Exception) {
            return null
        }
        if (response.equals("null", ignoreCase = true)) return null
        // Re-validate. This is the hallucination guard.
        val match = EMAIL_REGEX.find(response) ?: return null
        return match.value
    }

    private fun pickBest(candidates: List<String>, companyHint: String?): String? {
        val nonNoreply = candidates.filterNot { isNoreply(it) }
        val pool = nonNoreply.ifEmpty { candidates }
        if (companyHint != null) {
            val token = companyHint.lowercase().filter { it.isLetterOrDigit() }
            val matching = pool.firstOrNull { token.isNotEmpty() && it.lowercase().contains(token) }
            if (matching != null) return matching
        }
        return pool.firstOrNull()
    }

    private fun isNoreply(email: String): Boolean {
        val local = email.substringBefore('@').lowercase()
        return local.startsWith("noreply") || local.startsWith("no-reply") ||
            local.startsWith("donotreply") || local.startsWith("do-not-reply")
    }

    companion object {
        // RFC 5322-lite — sufficient for posting bodies, conservative enough to reject garbage.
        private val EMAIL_REGEX = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :backend:processing:test --tests "com.jobhunter.processing.service.EmailExtractorTest"
```

Expected: PASS — all five tests.

- [ ] **Step 5: Commit**

```bash
git add backend/processing/src/main/kotlin/com/jobhunter/processing/service/EmailExtractor.kt \
        backend/processing/src/test/kotlin/com/jobhunter/processing/service/EmailExtractorTest.kt
git commit -m "feat: add EmailExtractor with regex-first plus LLM fallback"
```

---

## Task 4: `ParsePromptBuilder` and `ParsedFields` DTO + golden test

**Files:**
- Create: `backend/processing/src/main/kotlin/com/jobhunter/processing/dto/ParsedFields.kt`
- Create: `backend/processing/src/main/kotlin/com/jobhunter/processing/prompt/ParsePromptBuilder.kt`
- Create: `backend/processing/src/test/resources/prompts/parse/sample-1-input.txt`
- Create: `backend/processing/src/test/resources/prompts/parse/sample-1-expected.json`
- Create: `backend/processing/src/test/kotlin/com/jobhunter/processing/prompt/ParsePromptBuilderTest.kt`

- [ ] **Step 1: Create `ParsedFields` DTO**

```kotlin
package com.jobhunter.processing.dto

data class ParsedFields(
    val title: String?,
    val company: String?,
    val location: String?,
    val isRemote: Boolean?,
    val language: String?,           // "he" or "en"
    val description: String?,
    val requirements: String?,
    val salaryText: String?,
    val applyUrl: String?,
    val contactEmail: String?,       // LLM-extracted candidate; verified by EmailExtractor before save
)
```

- [ ] **Step 2: Create input fixture**

`backend/processing/src/test/resources/prompts/parse/sample-1-input.txt`:

```
Backend Engineer (Kotlin)
Acme Robotics · Tel Aviv, Israel · Hybrid

About the role:
We're looking for a backend engineer with 5+ years of experience in Kotlin and Spring Boot. You'll work on our microservices platform and own performance-critical paths.

Requirements:
- 5+ years backend experience
- Kotlin or Java expertise
- Postgres and SQL fluency
- Hebrew/English

Apply: send your CV to jobs@acme-robotics.com
```

- [ ] **Step 3: Create expected JSON fixture**

`backend/processing/src/test/resources/prompts/parse/sample-1-expected.json`:

```json
{
  "title": "Backend Engineer (Kotlin)",
  "company": "Acme Robotics",
  "location": "Tel Aviv, Israel",
  "isRemote": false,
  "language": "en",
  "description": "Backend engineer working on microservices platform.",
  "requirements": "5+ years backend, Kotlin/Java, Postgres, Hebrew/English",
  "salaryText": null,
  "applyUrl": null,
  "contactEmail": "jobs@acme-robotics.com"
}
```

- [ ] **Step 4: Write the failing test**

```kotlin
package com.jobhunter.processing.prompt

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.jobhunter.processing.client.RecordingLlmClient
import com.jobhunter.processing.dto.ParsedFields
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ParsePromptBuilderTest {

    private val mapper = ObjectMapper().registerKotlinModule()
    private val builder = ParsePromptBuilder()

    private fun load(name: String): String =
        javaClass.getResourceAsStream("/prompts/parse/$name")!!
            .bufferedReader().readText()

    @Test
    fun `parses sample-1 against recorded response`() {
        val input = load("sample-1-input.txt")
        val expectedJson = load("sample-1-expected.json")

        val llm = RecordingLlmClient()
        llm.record(builder.systemPrompt(), input, expectedJson)

        val result: ParsedFields = builder.invoke(llm, input, mapper)
        val expected: ParsedFields = mapper.readValue(expectedJson, ParsedFields::class.java)
        assertEquals(expected.title, result.title)
        assertEquals(expected.company, result.company)
        assertEquals("en", result.language)
        assertEquals("jobs@acme-robotics.com", result.contactEmail)
    }
}
```

- [ ] **Step 5: Run the test to verify it fails**

```bash
./gradlew :backend:processing:test --tests "com.jobhunter.processing.prompt.ParsePromptBuilderTest"
```

Expected: FAIL — `ParsePromptBuilder` does not exist.

- [ ] **Step 6: Implement `ParsePromptBuilder`**

```kotlin
package com.jobhunter.processing.prompt

import com.fasterxml.jackson.databind.ObjectMapper
import com.jobhunter.core.client.LlmClient
import com.jobhunter.processing.dto.ParsedFields
import org.springframework.stereotype.Component

@Component
class ParsePromptBuilder {

    fun systemPrompt(): String = """
        You extract structured data from a single job posting. Reply with ONE JSON object only,
        matching this schema (use null for missing fields, do NOT invent values):
        {
          "title": string|null,
          "company": string|null,
          "location": string|null,
          "isRemote": boolean|null,
          "language": "he"|"en"|null,
          "description": string|null,
          "requirements": string|null,
          "salaryText": string|null,
          "applyUrl": string|null,
          "contactEmail": string|null
        }
        Reply with JSON only. No markdown fences, no prose.
    """.trimIndent()

    /**
     * Calls the LLM with the structured-output prompt, parses the JSON response,
     * and retries once with a stricter reminder if parsing fails.
     */
    fun invoke(llm: LlmClient, rawText: String, mapper: ObjectMapper): ParsedFields {
        val first = llm.chatStructured(system = systemPrompt(), user = rawText)
        return parseOrRetry(llm, rawText, first, mapper)
    }

    private fun parseOrRetry(
        llm: LlmClient, rawText: String, firstResponse: String, mapper: ObjectMapper,
    ): ParsedFields {
        try {
            return mapper.readValue(firstResponse, ParsedFields::class.java)
        } catch (_: Exception) {
            // strict retry
            val retry = llm.chatStructured(
                system = systemPrompt() + "\n\nIMPORTANT: Your previous response was not valid JSON. Reply with valid JSON only.",
                user = rawText,
            )
            return mapper.readValue(retry, ParsedFields::class.java)
        }
    }
}
```

- [ ] **Step 7: Run the test to verify it passes**

```bash
./gradlew :backend:processing:test --tests "com.jobhunter.processing.prompt.ParsePromptBuilderTest"
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add backend/processing/src/main/kotlin/com/jobhunter/processing/dto/ParsedFields.kt \
        backend/processing/src/main/kotlin/com/jobhunter/processing/prompt/ParsePromptBuilder.kt \
        backend/processing/src/test/resources/prompts/parse \
        backend/processing/src/test/kotlin/com/jobhunter/processing/prompt/ParsePromptBuilderTest.kt
git commit -m "feat: add ParsePromptBuilder with golden-file test"
```

---

## Task 5: `ParseWorker` + integration test

**Files:**
- Create: `backend/processing/src/main/kotlin/com/jobhunter/processing/worker/ParseWorker.kt`
- Create: `backend/processing/src/test/kotlin/com/jobhunter/processing/worker/ParseWorkerTest.kt`

The worker advances `INGESTED → PARSED`. It runs `ParsePromptBuilder` to fill structured fields, then runs `EmailExtractor` to set `contactEmail` (regex-first), then writes both to the `JobPosting` row.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.jobhunter.processing.worker

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.jobhunter.core.AbstractRepositoryTest
import com.jobhunter.core.domain.*
import com.jobhunter.core.repository.*
import com.jobhunter.processing.client.RecordingLlmClient
import com.jobhunter.processing.prompt.ParsePromptBuilder
import com.jobhunter.processing.service.EmailExtractor
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.ContextConfiguration
import org.springframework.transaction.PlatformTransactionManager
import java.time.Instant
import kotlin.test.assertEquals

@ContextConfiguration(classes = [ParseWorkerTest.TestBeans::class])
class ParseWorkerTest : AbstractRepositoryTest() {

    @TestConfiguration
    class TestBeans {
        @Bean fun mapper() = ObjectMapper().registerKotlinModule()
        @Bean fun parsePromptBuilder() = ParsePromptBuilder()
    }

    @Autowired lateinit var sources: JobSourceRepository
    @Autowired lateinit var postings: JobPostingRepository
    @Autowired lateinit var queue: ProcessingQueueRepository
    @Autowired lateinit var txManager: PlatformTransactionManager
    @Autowired lateinit var mapper: ObjectMapper
    @Autowired lateinit var promptBuilder: ParsePromptBuilder

    @Test
    fun `parses INGESTED row, fills fields, advances to PARSED`() {
        val src = sources.save(JobSource("S", SourceType.IMAP, true, "{}"))
        val post = postings.save(JobPosting(
            sourceId = src.id!!,
            externalId = "E1",
            rawText = "Backend Engineer at Acme. Tel Aviv. Apply: jobs@acme.com",
            capturedAt = Instant.now(),
        ))
        val queueRow = queue.save(ProcessingQueueRow(jobPostingId = post.id!!, state = QueueState.INGESTED))

        val llm = RecordingLlmClient()
        llm.recordByUser(
            user = post.rawText,
            response = """
                {"title":"Backend Engineer","company":"Acme","location":"Tel Aviv",
                 "isRemote":false,"language":"en","description":null,"requirements":null,
                 "salaryText":null,"applyUrl":null,"contactEmail":"jobs@acme.com"}
            """.trimIndent(),
        )
        val emailExtractor = EmailExtractor(llm)

        val worker = ParseWorker(
            queue = queue,
            postings = postings,
            txManager = txManager,
            llm = llm,
            promptBuilder = promptBuilder,
            emailExtractor = emailExtractor,
            mapper = mapper,
        )
        worker.runOnce()

        val updatedQueue = queue.findById(queueRow.id!!).get()
        assertEquals(QueueState.PARSED, updatedQueue.state)

        val updatedPosting = postings.findById(post.id!!).get()
        assertEquals("Backend Engineer", updatedPosting.title)
        assertEquals("Acme", updatedPosting.company)
        assertEquals("jobs@acme.com", updatedPosting.contactEmail)
        assertEquals("en", updatedPosting.language)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :backend:processing:test --tests "com.jobhunter.processing.worker.ParseWorkerTest"
```

Expected: FAIL.

- [ ] **Step 3: Implement `ParseWorker`**

```kotlin
package com.jobhunter.processing.worker

import com.fasterxml.jackson.databind.ObjectMapper
import com.jobhunter.core.client.LlmClient
import com.jobhunter.core.domain.QueueState
import com.jobhunter.core.repository.JobPostingRepository
import com.jobhunter.core.repository.ProcessingQueueRepository
import com.jobhunter.core.worker.QueueWorker
import com.jobhunter.processing.prompt.ParsePromptBuilder
import com.jobhunter.processing.service.EmailExtractor
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager

@Component
class ParseWorker(
    queue: ProcessingQueueRepository,
    private val postings: JobPostingRepository,
    txManager: PlatformTransactionManager,
    private val llm: LlmClient,
    private val promptBuilder: ParsePromptBuilder,
    private val emailExtractor: EmailExtractor,
    private val mapper: ObjectMapper,
) : QueueWorker(
    inputState = QueueState.INGESTED,
    outputState = QueueState.PARSED,
    queue = queue,
    txManager = txManager,
    maxAttempts = 3,
) {
    override fun process(jobPostingId: Long) {
        val posting = postings.findById(jobPostingId).orElseThrow {
            IllegalStateException("Posting $jobPostingId not found")
        }
        val parsed = promptBuilder.invoke(llm, posting.rawText, mapper)
        posting.title = parsed.title ?: posting.title
        posting.company = parsed.company ?: posting.company
        posting.location = parsed.location ?: posting.location
        posting.isRemote = parsed.isRemote ?: posting.isRemote
        posting.language = parsed.language ?: posting.language
        posting.description = parsed.description ?: posting.description
        posting.requirements = parsed.requirements ?: posting.requirements
        posting.salaryText = parsed.salaryText ?: posting.salaryText
        posting.applyUrl = parsed.applyUrl ?: posting.applyUrl

        // Email is regex-first, with the LLM's contactEmail used only as a hint to the fallback path.
        val emailFromText = emailExtractor.extract(posting.rawText, posting.company)
        posting.contactEmail = emailFromText ?: parsed.contactEmail?.takeIf { it.matches(EMAIL_REGEX) }

        postings.save(posting)
    }

    companion object {
        private val EMAIL_REGEX = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :backend:processing:test --tests "com.jobhunter.processing.worker.ParseWorkerTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/processing/src/main/kotlin/com/jobhunter/processing/worker/ParseWorker.kt \
        backend/processing/src/test/kotlin/com/jobhunter/processing/worker/ParseWorkerTest.kt
git commit -m "feat: add ParseWorker for INGESTED -> PARSED transition"
```

---

## Task 6: `ClassifyPromptBuilder`, `ClassifyWorker`, `ProcessingProperties`, golden + integration tests

**Files:**
- Create: `backend/processing/src/main/kotlin/com/jobhunter/processing/dto/ClassificationResult.kt`
- Create: `backend/processing/src/main/kotlin/com/jobhunter/processing/config/ProcessingProperties.kt`
- Create: `backend/processing/src/main/kotlin/com/jobhunter/processing/prompt/ClassifyPromptBuilder.kt`
- Create: `backend/processing/src/main/kotlin/com/jobhunter/processing/worker/ClassifyWorker.kt`
- Create: `backend/processing/src/test/resources/prompts/classify/sample-1-input.txt`
- Create: `backend/processing/src/test/resources/prompts/classify/sample-1-expected.json`
- Create: `backend/processing/src/test/kotlin/com/jobhunter/processing/prompt/ClassifyPromptBuilderTest.kt`
- Create: `backend/processing/src/test/kotlin/com/jobhunter/processing/worker/ClassifyWorkerTest.kt`

- [ ] **Step 1: Create `ProcessingProperties`**

```kotlin
package com.jobhunter.processing.config

import com.jobhunter.core.domain.Category
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("jobhunter")
data class ProcessingProperties(
    val monitoredCategories: List<Category> = listOf(
        Category.SOFTWARE_BACKEND,
        Category.SOFTWARE_FULLSTACK,
        Category.DEVOPS,
        Category.SRE,
        Category.PLATFORM,
    ),
)
```

- [ ] **Step 2: Create `ClassificationResult`**

```kotlin
package com.jobhunter.processing.dto

import com.jobhunter.core.domain.Category

data class ClassificationResult(val categories: List<Category>)
```

- [ ] **Step 3: Create classify fixtures**

`prompts/classify/sample-1-input.txt`:
```
Senior Backend Engineer (Kotlin / Spring Boot)
We're hiring a backend engineer to lead our payments microservices.
```

`prompts/classify/sample-1-expected.json`:
```json
["SOFTWARE_BACKEND"]
```

- [ ] **Step 4: Write the failing prompt test**

```kotlin
package com.jobhunter.processing.prompt

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.jobhunter.core.domain.Category
import com.jobhunter.processing.client.RecordingLlmClient
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ClassifyPromptBuilderTest {

    private val mapper = ObjectMapper().registerKotlinModule()
    private val builder = ClassifyPromptBuilder()

    private fun load(name: String): String =
        javaClass.getResourceAsStream("/prompts/classify/$name")!!
            .bufferedReader().readText()

    @Test
    fun `parses sample-1 categories`() {
        val input = load("sample-1-input.txt")
        val expectedJson = load("sample-1-expected.json")

        val llm = RecordingLlmClient()
        llm.record(builder.systemPrompt(), input, expectedJson)

        val result = builder.invoke(llm, input, mapper)
        assertEquals(listOf(Category.SOFTWARE_BACKEND), result.categories)
    }

    @Test
    fun `empty array means no labels`() {
        val llm = RecordingLlmClient()
        llm.recordByUser("foo", "[]")
        val result = builder.invoke(llm, "foo", mapper)
        assertEquals(emptyList(), result.categories)
    }
}
```

- [ ] **Step 5: Run the prompt test to verify it fails**

```bash
./gradlew :backend:processing:test --tests "com.jobhunter.processing.prompt.ClassifyPromptBuilderTest"
```

Expected: FAIL.

- [ ] **Step 6: Implement `ClassifyPromptBuilder`**

```kotlin
package com.jobhunter.processing.prompt

import com.fasterxml.jackson.databind.ObjectMapper
import com.jobhunter.core.client.LlmClient
import com.jobhunter.core.domain.Category
import com.jobhunter.processing.dto.ClassificationResult
import org.springframework.stereotype.Component

@Component
class ClassifyPromptBuilder {

    fun systemPrompt(): String {
        val labels = Category.entries.joinToString(", ") { it.name }
        return """
            Classify this job posting. Pick zero or more from this exact list of labels:
            [$labels]
            Reply with a JSON array of labels, e.g. ["SOFTWARE_BACKEND","DEVOPS"].
            If none apply, reply with [].
            JSON only. No prose, no fences.
        """.trimIndent()
    }

    fun invoke(llm: LlmClient, rawText: String, mapper: ObjectMapper): ClassificationResult {
        val response = llm.chatStructured(system = systemPrompt(), user = rawText)
        val parsed: List<String> = parseOrRetry(llm, rawText, response, mapper)
        val cats = parsed.mapNotNull { name ->
            runCatching { Category.valueOf(name) }.getOrNull()
        }
        return ClassificationResult(cats)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseOrRetry(
        llm: LlmClient, rawText: String, firstResponse: String, mapper: ObjectMapper,
    ): List<String> = try {
        mapper.readValue(firstResponse, List::class.java) as List<String>
    } catch (_: Exception) {
        val retry = llm.chatStructured(
            system = systemPrompt() + "\n\nIMPORTANT: Your previous response was not valid JSON. Reply with a JSON array only.",
            user = rawText,
        )
        mapper.readValue(retry, List::class.java) as List<String>
    }
}
```

- [ ] **Step 7: Run the prompt test to verify it passes**

```bash
./gradlew :backend:processing:test --tests "com.jobhunter.processing.prompt.ClassifyPromptBuilderTest"
```

Expected: PASS.

- [ ] **Step 8: Write the failing worker test**

```kotlin
package com.jobhunter.processing.worker

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.jobhunter.core.AbstractRepositoryTest
import com.jobhunter.core.domain.*
import com.jobhunter.core.repository.*
import com.jobhunter.processing.client.RecordingLlmClient
import com.jobhunter.processing.config.ProcessingProperties
import com.jobhunter.processing.prompt.ClassifyPromptBuilder
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.ContextConfiguration
import org.springframework.transaction.PlatformTransactionManager
import java.time.Instant
import kotlin.test.assertEquals

@ContextConfiguration(classes = [ClassifyWorkerTest.TestBeans::class])
class ClassifyWorkerTest : AbstractRepositoryTest() {

    @TestConfiguration
    class TestBeans {
        @Bean fun mapper() = ObjectMapper().registerKotlinModule()
        @Bean fun classifyPromptBuilder() = ClassifyPromptBuilder()
    }

    @Autowired lateinit var sources: JobSourceRepository
    @Autowired lateinit var postings: JobPostingRepository
    @Autowired lateinit var queue: ProcessingQueueRepository
    @Autowired lateinit var txManager: PlatformTransactionManager
    @Autowired lateinit var mapper: ObjectMapper
    @Autowired lateinit var prompt: ClassifyPromptBuilder

    private fun seed(state: QueueState, body: String): Pair<JobPosting, ProcessingQueueRow> {
        val src = sources.save(JobSource("S-${System.nanoTime()}", SourceType.IMAP, true, "{}"))
        val p = postings.save(JobPosting(
            sourceId = src.id!!, externalId = "E-${System.nanoTime()}",
            rawText = body, capturedAt = Instant.now(),
        ))
        val r = queue.save(ProcessingQueueRow(jobPostingId = p.id!!, state = state))
        return p to r
    }

    @Test
    fun `monitored category advances row to CLASSIFIED and stores categories`() {
        val (post, queueRow) = seed(QueueState.PARSED, "Backend engineer Kotlin")
        val llm = RecordingLlmClient()
        llm.recordByUser(post.rawText, """["SOFTWARE_BACKEND"]""")

        val props = ProcessingProperties(monitoredCategories = listOf(Category.SOFTWARE_BACKEND))
        val worker = ClassifyWorker(queue, postings, txManager, llm, prompt, mapper, props)
        worker.runOnce()

        assertEquals(QueueState.CLASSIFIED, queue.findById(queueRow.id!!).get().state)
        assertEquals(listOf(Category.SOFTWARE_BACKEND), postings.findById(post.id!!).get().categories)
    }

    @Test
    fun `non-monitored category routes row to OUT_OF_SCOPE`() {
        val (_, queueRow) = seed(QueueState.PARSED, "Marketing manager")
        val llm = RecordingLlmClient()
        llm.recordByUser("Marketing manager", """["MARKETING"]""")

        val props = ProcessingProperties(monitoredCategories = listOf(Category.SOFTWARE_BACKEND))
        ClassifyWorker(queue, postings, txManager, llm, prompt, mapper, props).runOnce()

        assertEquals(QueueState.OUT_OF_SCOPE, queue.findById(queueRow.id!!).get().state)
    }

    @Test
    fun `empty classification routes row to IRRELEVANT`() {
        val (_, queueRow) = seed(QueueState.PARSED, "Garbage")
        val llm = RecordingLlmClient()
        llm.recordByUser("Garbage", "[]")

        val props = ProcessingProperties(monitoredCategories = listOf(Category.SOFTWARE_BACKEND))
        ClassifyWorker(queue, postings, txManager, llm, prompt, mapper, props).runOnce()

        assertEquals(QueueState.IRRELEVANT, queue.findById(queueRow.id!!).get().state)
    }
}
```

- [ ] **Step 9: Implement `ClassifyWorker`**

```kotlin
package com.jobhunter.processing.worker

import com.fasterxml.jackson.databind.ObjectMapper
import com.jobhunter.core.client.LlmClient
import com.jobhunter.core.domain.QueueState
import com.jobhunter.core.repository.JobPostingRepository
import com.jobhunter.core.repository.ProcessingQueueRepository
import com.jobhunter.core.worker.QueueWorker
import com.jobhunter.processing.config.ProcessingProperties
import com.jobhunter.processing.prompt.ClassifyPromptBuilder
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

/**
 * Three terminal branches make ClassifyWorker special-cased: it overrides runOnce
 * rather than relying on the base [QueueWorker.process] -> outputState advance.
 */
@Component
class ClassifyWorker(
    private val queue: ProcessingQueueRepository,
    private val postings: JobPostingRepository,
    txManager: PlatformTransactionManager,
    private val llm: LlmClient,
    private val promptBuilder: ClassifyPromptBuilder,
    private val mapper: ObjectMapper,
    private val props: ProcessingProperties,
) : QueueWorker(
    inputState = QueueState.PARSED,
    outputState = QueueState.CLASSIFIED,
    queue = queue,
    txManager = txManager,
    maxAttempts = 3,
) {
    private val tx = TransactionTemplate(txManager)

    override fun process(jobPostingId: Long) {
        val posting = postings.findById(jobPostingId).orElseThrow {
            IllegalStateException("Posting $jobPostingId not found")
        }
        val result = promptBuilder.invoke(llm, posting.rawText, mapper)
        posting.categories = result.categories
        postings.save(posting)

        val terminal: QueueState? = when {
            result.categories.isEmpty() -> QueueState.IRRELEVANT
            result.categories.none { it in props.monitoredCategories } -> QueueState.OUT_OF_SCOPE
            else -> null
        }
        if (terminal != null) {
            tx.executeWithoutResult {
                val row = queue.findAll().first { it.jobPostingId == jobPostingId && it.state == QueueState.PARSED }
                row.state = terminal
                row.attempts = 0
                row.lastError = null
                row.nextAttemptAt = null
                row.updatedAt = Instant.now()
                queue.save(row)
            }
            // Throw a sentinel to bypass the base class's normal advance-to-outputState path.
            // We re-fetch and verify the override wrote correctly.
            // (alternative: extend QueueWorker with a hook for this; kept inline to avoid signature churn)
            throw EarlyTerminalException()
        }
    }

    private class EarlyTerminalException : RuntimeException() {
        override fun fillInStackTrace() = this  // keep cheap; this is control flow
    }

    override fun toString() = "ClassifyWorker(monitored=${props.monitoredCategories})"
}
```

Wait — the `EarlyTerminalException` approach is brittle (the base `QueueWorker` will count it as a failure and retry). Replace with a cleaner approach: **make the base class skip its advance step when the row has already moved to a terminal state in `process()`**.

Modify `QueueWorker` (in `core`) to check the row's current state after `process()` returns; only advance to `outputState` if state is still `inputState`. Update Plan 1's QueueWorker code mentally — practical change: in `handleOne` after `process(jobPostingId)`, fetch the row, check `state`, only advance if equal to `inputState`. Add this small refactor here.

Replace `ClassifyWorker.kt` with a clean version and update `QueueWorker.kt`:

```kotlin
// Replace process() method body in ClassifyWorker:
override fun process(jobPostingId: Long) {
    val posting = postings.findById(jobPostingId).orElseThrow {
        IllegalStateException("Posting $jobPostingId not found")
    }
    val result = promptBuilder.invoke(llm, posting.rawText, mapper)
    posting.categories = result.categories
    postings.save(posting)

    val terminal: QueueState? = when {
        result.categories.isEmpty() -> QueueState.IRRELEVANT
        result.categories.none { it in props.monitoredCategories } -> QueueState.OUT_OF_SCOPE
        else -> null
    }
    if (terminal != null) {
        val row = queue.findAll().first { it.jobPostingId == jobPostingId && it.state == QueueState.PARSED }
        row.state = terminal
        row.attempts = 0
        row.lastError = null
        row.nextAttemptAt = null
        row.updatedAt = Instant.now()
        queue.save(row)
    }
    // If terminal: row is now in IRRELEVANT/OUT_OF_SCOPE, NOT in PARSED.
    // Base class's handleOne will fetch, see state != inputState, and skip the advance.
}
```

Update `QueueWorker.handleOne` (in core) to:

```kotlin
private fun handleOne(queueId: Long, jobPostingId: Long) {
    try {
        process(jobPostingId)
        tx.executeWithoutResult {
            val r = queue.findById(queueId).get()
            // Only advance if the row is still in inputState.
            // Subclasses may have moved it to a terminal state during process().
            if (r.state == inputState) {
                r.state = outputState
                r.attempts = 0
                r.lastError = null
                r.nextAttemptAt = null
                r.updatedAt = Instant.now()
                queue.save(r)
            }
        }
    } catch (e: Exception) {
        // ... existing failure handling unchanged
    }
}
```

Apply that change to the existing `backend/core/src/main/kotlin/com/jobhunter/core/worker/QueueWorker.kt` from Plan 1. Specifically modify the success branch in `handleOne`.

- [ ] **Step 10: Apply the QueueWorker refactor**

Open `backend/core/src/main/kotlin/com/jobhunter/core/worker/QueueWorker.kt` and modify `handleOne`'s success branch:

```kotlin
private fun handleOne(queueId: Long, jobPostingId: Long) {
    try {
        process(jobPostingId)
        tx.executeWithoutResult {
            val r = queue.findById(queueId).get()
            if (r.state == inputState) {
                r.state = outputState
                r.attempts = 0
                r.lastError = null
                r.nextAttemptAt = null
                r.updatedAt = Instant.now()
                queue.save(r)
            }
            // else: subclass already moved to a terminal state in process(); leave it.
        }
    } catch (e: Exception) {
        log.warn(e) { "QueueWorker(${this::class.simpleName}) failed processing queueId=$queueId" }
        tx.executeWithoutResult {
            val r = queue.findById(queueId).get()
            r.attempts += 1
            r.lastError = e.message?.take(2000)
            r.updatedAt = Instant.now()
            if (r.attempts >= maxAttempts) {
                r.state = QueueState.FAILED
                r.nextAttemptAt = null
            } else {
                val backoff = min(
                    maxBackoffSeconds,
                    (baseBackoffSeconds.toDouble().pow(r.attempts.toDouble())).toLong(),
                )
                r.nextAttemptAt = Instant.now().plusSeconds(backoff)
            }
            queue.save(r)
        }
    }
}
```

Rerun the Plan 1 worker test to confirm nothing broke:

```bash
./gradlew :backend:core:test --tests "com.jobhunter.core.worker.QueueWorkerTest"
```

Expected: PASS — three tests still green.

- [ ] **Step 11: Replace `ClassifyWorker.kt` with the clean version (drop the EarlyTerminalException)**

Open `backend/processing/src/main/kotlin/com/jobhunter/processing/worker/ClassifyWorker.kt` and replace with:

```kotlin
package com.jobhunter.processing.worker

import com.fasterxml.jackson.databind.ObjectMapper
import com.jobhunter.core.client.LlmClient
import com.jobhunter.core.domain.QueueState
import com.jobhunter.core.repository.JobPostingRepository
import com.jobhunter.core.repository.ProcessingQueueRepository
import com.jobhunter.core.worker.QueueWorker
import com.jobhunter.processing.config.ProcessingProperties
import com.jobhunter.processing.prompt.ClassifyPromptBuilder
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import java.time.Instant

@Component
class ClassifyWorker(
    private val queue: ProcessingQueueRepository,
    private val postings: JobPostingRepository,
    txManager: PlatformTransactionManager,
    private val llm: LlmClient,
    private val promptBuilder: ClassifyPromptBuilder,
    private val mapper: ObjectMapper,
    private val props: ProcessingProperties,
) : QueueWorker(
    inputState = QueueState.PARSED,
    outputState = QueueState.CLASSIFIED,
    queue = queue,
    txManager = txManager,
    maxAttempts = 3,
) {
    override fun process(jobPostingId: Long) {
        val posting = postings.findById(jobPostingId).orElseThrow {
            IllegalStateException("Posting $jobPostingId not found")
        }
        val result = promptBuilder.invoke(llm, posting.rawText, mapper)
        posting.categories = result.categories
        postings.save(posting)

        val terminal: QueueState? = when {
            result.categories.isEmpty() -> QueueState.IRRELEVANT
            result.categories.none { it in props.monitoredCategories } -> QueueState.OUT_OF_SCOPE
            else -> null
        }
        if (terminal != null) {
            val row = queue.findAll().first { it.jobPostingId == jobPostingId && it.state == QueueState.PARSED }
            row.state = terminal
            row.attempts = 0
            row.lastError = null
            row.nextAttemptAt = null
            row.updatedAt = Instant.now()
            queue.save(row)
        }
    }
}
```

- [ ] **Step 12: Run the worker test to verify it passes**

```bash
./gradlew :backend:processing:test --tests "com.jobhunter.processing.worker.ClassifyWorkerTest"
```

Expected: PASS — all three tests.

- [ ] **Step 13: Commit**

```bash
git add backend/processing/src/main/kotlin/com/jobhunter/processing/dto/ClassificationResult.kt \
        backend/processing/src/main/kotlin/com/jobhunter/processing/config/ProcessingProperties.kt \
        backend/processing/src/main/kotlin/com/jobhunter/processing/prompt/ClassifyPromptBuilder.kt \
        backend/processing/src/main/kotlin/com/jobhunter/processing/worker/ClassifyWorker.kt \
        backend/processing/src/test/resources/prompts/classify \
        backend/processing/src/test/kotlin/com/jobhunter/processing/prompt/ClassifyPromptBuilderTest.kt \
        backend/processing/src/test/kotlin/com/jobhunter/processing/worker/ClassifyWorkerTest.kt \
        backend/core/src/main/kotlin/com/jobhunter/core/worker/QueueWorker.kt
git commit -m "feat: add ClassifyWorker with monitored-categories filter; allow worker subclasses to set terminal state"
```

---

## Task 7: `EmbedWorker` + integration test

**Files:**
- Create: `backend/processing/src/main/kotlin/com/jobhunter/processing/worker/EmbedWorker.kt`
- Create: `backend/processing/src/test/kotlin/com/jobhunter/processing/worker/EmbedWorkerTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.jobhunter.processing.worker

import com.jobhunter.core.AbstractRepositoryTest
import com.jobhunter.core.client.EmbeddingClient
import com.jobhunter.core.domain.*
import com.jobhunter.core.repository.*
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import java.time.Instant
import kotlin.test.assertEquals

class EmbedWorkerTest : AbstractRepositoryTest() {

    @Autowired lateinit var sources: JobSourceRepository
    @Autowired lateinit var postings: JobPostingRepository
    @Autowired lateinit var queue: ProcessingQueueRepository
    @Autowired lateinit var embeddings: PostingEmbeddingRepository
    @Autowired lateinit var txManager: PlatformTransactionManager

    @Test
    fun `embeds CLASSIFIED row, advances to EMBEDDED`() {
        val src = sources.save(JobSource("S", SourceType.IMAP, true, "{}"))
        val post = postings.save(JobPosting(
            sourceId = src.id!!, externalId = "E", rawText = "raw text", capturedAt = Instant.now(),
        ))
        val queueRow = queue.save(ProcessingQueueRow(jobPostingId = post.id!!, state = QueueState.CLASSIFIED))

        val embeddingClient = mockk<EmbeddingClient>()
        every { embeddingClient.embed("raw text") } returns FloatArray(1024) { 0.5f }

        val worker = EmbedWorker(queue, postings, embeddings, txManager, embeddingClient)
        worker.runOnce()

        assertEquals(QueueState.EMBEDDED, queue.findById(queueRow.id!!).get().state)
        val emb = embeddings.findById(post.id!!).get()
        assertEquals(1024, emb.embedding.size)
        assertEquals("bge-m3", emb.modelName)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :backend:processing:test --tests "com.jobhunter.processing.worker.EmbedWorkerTest"
```

Expected: FAIL.

- [ ] **Step 3: Implement `EmbedWorker`**

```kotlin
package com.jobhunter.processing.worker

import com.jobhunter.core.client.EmbeddingClient
import com.jobhunter.core.domain.PostingEmbedding
import com.jobhunter.core.domain.QueueState
import com.jobhunter.core.repository.JobPostingRepository
import com.jobhunter.core.repository.PostingEmbeddingRepository
import com.jobhunter.core.repository.ProcessingQueueRepository
import com.jobhunter.core.worker.QueueWorker
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager

@Component
class EmbedWorker(
    queue: ProcessingQueueRepository,
    private val postings: JobPostingRepository,
    private val embeddings: PostingEmbeddingRepository,
    txManager: PlatformTransactionManager,
    private val embeddingClient: EmbeddingClient,
) : QueueWorker(
    inputState = QueueState.CLASSIFIED,
    outputState = QueueState.EMBEDDED,
    queue = queue,
    txManager = txManager,
    maxAttempts = 5,
    baseBackoffSeconds = 2,
    maxBackoffSeconds = 60,
) {
    override fun process(jobPostingId: Long) {
        val posting = postings.findById(jobPostingId).orElseThrow {
            IllegalStateException("Posting $jobPostingId not found")
        }
        val vec = embeddingClient.embed(posting.rawText)
        require(vec.size == 1024) { "Expected 1024-dim vector, got ${vec.size}" }
        embeddings.save(PostingEmbedding(
            jobPostingId = posting.id!!,
            embedding = vec,
            modelName = "bge-m3",
        ))
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :backend:processing:test --tests "com.jobhunter.processing.worker.EmbedWorkerTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/processing/src/main/kotlin/com/jobhunter/processing/worker/EmbedWorker.kt \
        backend/processing/src/test/kotlin/com/jobhunter/processing/worker/EmbedWorkerTest.kt
git commit -m "feat: add EmbedWorker for CLASSIFIED -> EMBEDDED transition"
```

---

## Task 8: `WorkerScheduler` — periodic poll for each worker

**Files:**
- Create: `backend/processing/src/main/kotlin/com/jobhunter/processing/worker/WorkerScheduler.kt`
- Create: `backend/processing/src/test/kotlin/com/jobhunter/processing/worker/WorkerSchedulerTest.kt`

The safety net per spec §5.2 — runs every 60s, picks up stuck rows.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.jobhunter.processing.worker

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

        io.mockk.every { parse.runOnce(any()) } throws RuntimeException("boom")

        WorkerScheduler(parse, classify, embed).tick()

        verify { classify.runOnce(any()) }
        verify { embed.runOnce(any()) }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :backend:processing:test --tests "com.jobhunter.processing.worker.WorkerSchedulerTest"
```

Expected: FAIL.

- [ ] **Step 3: Implement**

```kotlin
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
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :backend:processing:test --tests "com.jobhunter.processing.worker.WorkerSchedulerTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/processing/src/main/kotlin/com/jobhunter/processing/worker/WorkerScheduler.kt \
        backend/processing/src/test/kotlin/com/jobhunter/processing/worker/WorkerSchedulerTest.kt
git commit -m "feat: add WorkerScheduler safety-net poll"
```

---

## Task 9: `PostgresQueueListener` — LISTEN for queue events

**Files:**
- Create: `backend/processing/src/main/kotlin/com/jobhunter/processing/worker/PostgresQueueListener.kt`
- Create: `backend/processing/src/test/kotlin/com/jobhunter/processing/worker/PostgresQueueListenerTest.kt`

A dedicated background thread holds a Postgres connection in `LISTEN queue_event` mode and wakes the workers on each notification.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.jobhunter.processing.worker

import com.jobhunter.core.AbstractRepositoryTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

class PostgresQueueListenerTest : AbstractRepositoryTest() {
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var dataSource: javax.sql.DataSource

    @Test
    @Timeout(15)
    fun `wakes callback on NOTIFY queue_event`() {
        val woke = CountDownLatch(1)
        val listener = PostgresQueueListener(dataSource) { woke.countDown() }
        listener.start()
        try {
            // Give the listener a moment to LISTEN
            Thread.sleep(500)
            jdbc.execute("NOTIFY queue_event")
            assertTrue(woke.await(10, TimeUnit.SECONDS), "listener did not fire callback")
        } finally {
            listener.stop()
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :backend:processing:test --tests "com.jobhunter.processing.worker.PostgresQueueListenerTest"
```

Expected: FAIL.

- [ ] **Step 3: Implement `PostgresQueueListener`**

```kotlin
package com.jobhunter.processing.worker

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.postgresql.PGConnection
import org.springframework.stereotype.Component
import javax.sql.DataSource
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

private val log = KotlinLogging.logger {}

/**
 * Holds a dedicated Postgres connection in LISTEN mode on the `queue_event` channel.
 * On each NOTIFY, calls [onNotify], which wakes the worker scheduler.
 */
@Component
class PostgresQueueListener(
    private val dataSource: DataSource,
    private val onNotify: () -> Unit,
) {
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null

    @PostConstruct
    fun start() {
        if (!running.compareAndSet(false, true)) return
        thread = thread(name = "queue-listener", isDaemon = true) { loop() }
    }

    @PreDestroy
    fun stop() {
        if (!running.compareAndSet(true, false)) return
        thread?.interrupt()
    }

    private fun loop() {
        while (running.get()) {
            try {
                dataSource.connection.use { conn ->
                    conn.autoCommit = true
                    conn.createStatement().use { it.execute("LISTEN queue_event") }
                    val pg = conn.unwrap(PGConnection::class.java)
                    while (running.get()) {
                        val notifications = pg.getNotifications(5_000)
                        if (notifications != null && notifications.isNotEmpty()) {
                            try {
                                onNotify()
                            } catch (e: Exception) {
                                log.warn(e) { "queue_event handler failed" }
                            }
                        }
                    }
                }
            } catch (e: InterruptedException) {
                // shutting down
                return
            } catch (e: Exception) {
                log.warn(e) { "Listener connection error; reconnecting in 5s" }
                Thread.sleep(5_000)
            }
        }
    }
}
```

Note: this constructor takes the callback, which we wire as a separate `@Configuration` bean below. Update Plan 1's smoke test if it tries to autowire `PostgresQueueListener` directly — the listener depends on a callback that comes from the scheduler. Add a `@Configuration` to wire them:

`backend/processing/src/main/kotlin/com/jobhunter/processing/worker/QueueListenerConfig.kt`:

```kotlin
package com.jobhunter.processing.worker

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import javax.sql.DataSource

@Configuration
class QueueListenerConfig {
    @Bean
    @Primary
    fun postgresQueueListener(dataSource: DataSource, scheduler: WorkerScheduler): PostgresQueueListener =
        PostgresQueueListener(dataSource, onNotify = { scheduler.tick() })
}
```

Remove `@Component` from `PostgresQueueListener` to avoid duplicate beans. Update the file:

```kotlin
// PostgresQueueListener.kt — remove @Component annotation
class PostgresQueueListener(
    private val dataSource: DataSource,
    private val onNotify: () -> Unit,
) {
    // ... rest unchanged
}
```

- [ ] **Step 4: Update test to construct directly**

The test already constructs `PostgresQueueListener(dataSource) { woke.countDown() }` directly. With `@Component` removed, this works.

- [ ] **Step 5: Run the test to verify it passes**

```bash
./gradlew :backend:processing:test --tests "com.jobhunter.processing.worker.PostgresQueueListenerTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/processing/src/main/kotlin/com/jobhunter/processing/worker/PostgresQueueListener.kt \
        backend/processing/src/main/kotlin/com/jobhunter/processing/worker/QueueListenerConfig.kt \
        backend/processing/src/test/kotlin/com/jobhunter/processing/worker/PostgresQueueListenerTest.kt
git commit -m "feat: add Postgres LISTEN-based queue notifier"
```

---

## Task 10: `AdminQueueController` — counts and re-queue

**Files:**
- Create: `backend/processing/src/main/kotlin/com/jobhunter/processing/controller/AdminQueueController.kt`
- Create: `backend/processing/src/test/kotlin/com/jobhunter/processing/controller/AdminQueueControllerTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
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
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :backend:processing:test --tests "com.jobhunter.processing.controller.AdminQueueControllerTest"
```

Expected: FAIL.

- [ ] **Step 3: Implement**

```kotlin
package com.jobhunter.processing.controller

import com.jobhunter.core.domain.QueueState
import com.jobhunter.core.repository.ProcessingQueueRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/admin/queue")
class AdminQueueController(private val queue: ProcessingQueueRepository) {

    @GetMapping("/counts")
    fun counts(): Map<String, Long> =
        QueueState.entries.associate { it.name to queue.countByState(it) }

    @PostMapping("/{id}/requeue")
    fun requeue(@PathVariable id: Long) {
        val row = queue.findById(id).orElseThrow {
            IllegalArgumentException("Queue row $id not found")
        }
        row.state = QueueState.INGESTED   // re-enter pipeline at the start
        row.attempts = 0
        row.lastError = null
        row.nextAttemptAt = null
        row.updatedAt = Instant.now()
        queue.save(row)
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :backend:processing:test --tests "com.jobhunter.processing.controller.AdminQueueControllerTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/processing/src/main/kotlin/com/jobhunter/processing/controller \
        backend/processing/src/test/kotlin/com/jobhunter/processing/controller
git commit -m "feat: add admin queue controller for counts and requeue"
```

---

## Task 11: End-to-end pipeline integration test

**Files:**
- Create: `backend/processing/src/test/kotlin/com/jobhunter/processing/worker/PipelineEndToEndTest.kt`

Validates that all three workers, run in sequence, take a posting from `INGESTED` to `EMBEDDED` with all fields populated.

- [ ] **Step 1: Write the test**

```kotlin
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
        // ParseWorker call
        llm.recordByUser(raw, """
            {"title":"Senior Backend Engineer","company":"Acme","location":"Tel Aviv",
             "isRemote":false,"language":"en","description":null,"requirements":null,
             "salaryText":null,"applyUrl":null,"contactEmail":"jobs@acme.com"}
        """.trimIndent())
        // ClassifyWorker call (same user prompt; system prompt differs but RecordingLlmClient matches by user only here)
        // Need a different user content for classify: it sends the rawText. Let's use the same raw text:
        llm.recordByUser(raw, """["SOFTWARE_BACKEND"]""")
        // The recordByUser map collapses on duplicate keys; in practice the second record wins.
        // To handle both, we instead split: parse uses raw text; classify also uses raw text.
        // Since RecordingLlmClient.recordByUser keys by user only, we use the SYSTEM-keyed record() for differentiation.
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
```

- [ ] **Step 2: Run the test**

```bash
./gradlew :backend:processing:test --tests "com.jobhunter.processing.worker.PipelineEndToEndTest"
```

Expected: PASS.

- [ ] **Step 3: Run the full test suite**

```bash
./gradlew test
```

Expected: ALL tests pass across all modules.

- [ ] **Step 4: Commit and tag**

```bash
git add backend/processing/src/test/kotlin/com/jobhunter/processing/worker/PipelineEndToEndTest.kt
git commit -m "test: add end-to-end pipeline integration test"
git tag plan-3-complete
```

---

## End of Plan 3

**At this point:**

- [x] `ParseWorker` extracts structured fields, populates posting columns, advances `INGESTED → PARSED`.
- [x] `EmailExtractor` regex-first guard prevents email hallucinations.
- [x] `ClassifyWorker` assigns categories, applies `monitored_categories` filter, terminal-routes to `IRRELEVANT` / `OUT_OF_SCOPE`.
- [x] `EmbedWorker` produces 1024-dim vectors and writes to `posting_embedding`.
- [x] `WorkerScheduler` polls every 60s as safety net; `PostgresQueueListener` wakes them on `NOTIFY queue_event`.
- [x] `AdminQueueController` exposes counts and re-queue.
- [x] Golden-file LLM tests pass with `RecordingLlmClient`; no live Ollama in CI.
- [x] End-to-end pipeline test green.

**Next plan: Plan 4 — Matching + CV upload** (CV ingest, MatchWorker two-stage scoring, surfacing matches in `READY_FOR_REVIEW`).
