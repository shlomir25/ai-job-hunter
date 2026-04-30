# Plan 4 — Matching and CV Upload

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Accept a CV upload (PDF/DOCX), extract text + structured summary + embedding, persist as the active CV. Run a `MatchWorker` that takes `EMBEDDED` postings, scores them against the active CV in two stages (cosine filter, LLM rerank), and creates `Match` rows in `READY_FOR_REVIEW` for surface-able results.

**Architecture:** New `backend/matching` module. `CvService` orchestrates upload → Tika parse → LLM structured summary → bge-m3 embedding → save (with single-active-CV enforcement). `MatchWorker` is a `QueueWorker` that consumes `EMBEDDED` rows and routes each to `MATCHED` (Match row created) or `IRRELEVANT` (no Match row, terminal). `MatchService` exposes the read-side API for the React UI.

**Tech Stack additions:** Apache Tika for CV text extraction; Spring Boot multipart for upload.

**Reference spec:** `docs/superpowers/specs/2026-04-30-ai-job-hunter-design.md` §4.2 (matching module), §6.5 (cv table), §6.7 (match table), §7.3 (LLM matcher), §5.3 (user-driven flow).

**Depends on:** Plan 1 (entities, QueueWorker), Plan 3 (postings reach `EMBEDDED`).

---

## File Structure

**`backend/matching/` (new module):**
- `build.gradle.kts` — depends on `core`; adds `org.apache.tika:tika-core` + `tika-parsers-standard-package`
- `src/main/kotlin/com/jobhunter/matching/`
  - `dto/CvSummary.kt` — LLM-extracted summary
  - `dto/CvUploadResponse.kt`
  - `dto/MatchView.kt` — match + posting summary for UI
  - `dto/MatchScoreResult.kt` — LLM rerank output
  - `service/CvParseService.kt` — Tika wrapper
  - `service/CvService.kt` — upload orchestrator
  - `service/MatchService.kt` — read-side
  - `prompt/CvSummaryPromptBuilder.kt`
  - `prompt/MatchPromptBuilder.kt`
  - `worker/MatchWorker.kt`
  - `controller/CvController.kt`
  - `controller/MatchController.kt`
  - `config/MatchingProperties.kt` — bound from `jobhunter.matching.*`
- `src/test/kotlin/com/jobhunter/matching/...`
  - `service/CvParseServiceTest.kt`
  - `service/CvServiceTest.kt`
  - `prompt/CvSummaryPromptBuilderTest.kt`
  - `prompt/MatchPromptBuilderTest.kt`
  - `worker/MatchWorkerTest.kt`
  - `controller/CvControllerTest.kt`
  - `controller/MatchControllerTest.kt`
  - `EndToEndMatchingTest.kt`
- `src/test/resources/cv-fixtures/sample.pdf` (small fixture)
- `src/test/resources/prompts/{cv-summary,match}/sample-N-{input,expected}.{txt,json}`

**Root `settings.gradle.kts`** (modify): include `backend:matching`.
**`backend/app/build.gradle.kts`** (modify): add dependency on matching.

---

## Task 1: Bootstrap matching module

**Files:**
- Modify: `settings.gradle.kts`
- Create: `backend/matching/build.gradle.kts`
- Modify: `backend/app/build.gradle.kts`

- [ ] **Step 1: Update `settings.gradle.kts`**

```kotlin
rootProject.name = "ai-job-hunter"

include("backend:core", "backend:ingestion", "backend:processing", "backend:matching", "backend:app")

project(":backend:core").projectDir = file("backend/core")
project(":backend:ingestion").projectDir = file("backend/ingestion")
project(":backend:processing").projectDir = file("backend/processing")
project(":backend:matching").projectDir = file("backend/matching")
project(":backend:app").projectDir = file("backend/app")
```

- [ ] **Step 2: Create `backend/matching/build.gradle.kts`**

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

    implementation("org.apache.tika:tika-core:3.0.0")
    implementation("org.apache.tika:tika-parsers-standard-package:3.0.0")

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

Add `implementation(project(":backend:matching"))` to the dependencies block.

- [ ] **Step 4: Create directories**

```bash
mkdir -p backend/matching/src/main/kotlin/com/jobhunter/matching/{dto,service,prompt,worker,controller,config}
mkdir -p backend/matching/src/test/kotlin/com/jobhunter/matching/{service,prompt,worker,controller}
mkdir -p backend/matching/src/test/resources/{cv-fixtures,prompts/cv-summary,prompts/match}
```

- [ ] **Step 5: Verify build**

```bash
./gradlew :backend:matching:compileKotlin :backend:app:compileKotlin
```

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts backend/matching backend/app/build.gradle.kts
git commit -m "chore: bootstrap matching module"
```

---

## Task 2: `CvParseService` — Tika text extraction

**Files:**
- Create: `backend/matching/src/main/kotlin/com/jobhunter/matching/service/CvParseService.kt`
- Create: `backend/matching/src/test/kotlin/com/jobhunter/matching/service/CvParseServiceTest.kt`
- Create: `backend/matching/src/test/resources/cv-fixtures/sample.pdf` (any small PDF you have)

- [ ] **Step 1: Add a small PDF fixture**

Pick any small PDF (or generate one) and save to `backend/matching/src/test/resources/cv-fixtures/sample.pdf`. The test only needs Tika to extract some non-empty text.

If you don't have one handy, generate via:

```bash
echo "Sample CV: Backend engineer with Kotlin and Postgres experience." > /tmp/cv.txt
# On macOS:
cupsfilter /tmp/cv.txt > backend/matching/src/test/resources/cv-fixtures/sample.pdf
```

- [ ] **Step 2: Write the failing test**

```kotlin
package com.jobhunter.matching.service

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class CvParseServiceTest {
    private val service = CvParseService()

    @Test
    fun `extracts text from a PDF`() {
        val bytes = javaClass.getResourceAsStream("/cv-fixtures/sample.pdf")!!.readBytes()
        val text = service.extract(bytes, mimeType = "application/pdf", fileName = "sample.pdf")
        assertTrue(text.isNotBlank(), "Extracted text should be non-empty")
    }

    @Test
    fun `auto-detects mime type from bytes when null`() {
        val bytes = javaClass.getResourceAsStream("/cv-fixtures/sample.pdf")!!.readBytes()
        val text = service.extract(bytes, mimeType = null, fileName = "sample.pdf")
        assertTrue(text.isNotBlank())
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

```bash
./gradlew :backend:matching:test --tests "com.jobhunter.matching.service.CvParseServiceTest"
```

Expected: FAIL.

- [ ] **Step 4: Implement `CvParseService`**

```kotlin
package com.jobhunter.matching.service

import org.apache.tika.Tika
import org.apache.tika.metadata.Metadata
import org.apache.tika.parser.AutoDetectParser
import org.apache.tika.parser.ParseContext
import org.apache.tika.sax.BodyContentHandler
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream

@Service
class CvParseService {

    private val tika = Tika()

    fun extract(bytes: ByteArray, mimeType: String?, fileName: String): String {
        val handler = BodyContentHandler(-1)  // -1 = no character limit
        val metadata = Metadata().apply {
            set("resourceName", fileName)
            if (mimeType != null) set(Metadata.CONTENT_TYPE, mimeType)
        }
        val parser = AutoDetectParser()
        ByteArrayInputStream(bytes).use { input ->
            parser.parse(input, handler, metadata, ParseContext())
        }
        return handler.toString().trim()
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
./gradlew :backend:matching:test --tests "com.jobhunter.matching.service.CvParseServiceTest"
```

Expected: PASS — both tests.

- [ ] **Step 6: Commit**

```bash
git add backend/matching/src/main/kotlin/com/jobhunter/matching/service/CvParseService.kt \
        backend/matching/src/test/kotlin/com/jobhunter/matching/service/CvParseServiceTest.kt \
        backend/matching/src/test/resources/cv-fixtures
git commit -m "feat: add CvParseService with Tika text extraction"
```

---

## Task 3: `CvSummaryPromptBuilder` + golden test

**Files:**
- Create: `backend/matching/src/main/kotlin/com/jobhunter/matching/dto/CvSummary.kt`
- Create: `backend/matching/src/main/kotlin/com/jobhunter/matching/prompt/CvSummaryPromptBuilder.kt`
- Create: `backend/matching/src/test/resources/prompts/cv-summary/sample-1-input.txt`
- Create: `backend/matching/src/test/resources/prompts/cv-summary/sample-1-expected.json`
- Create: `backend/matching/src/test/kotlin/com/jobhunter/matching/prompt/CvSummaryPromptBuilderTest.kt`

The summary is what `MatchWorker` shows the LLM during scoring — keeping it structured saves tokens vs. passing raw CV text repeatedly.

- [ ] **Step 1: Create `CvSummary` DTO**

```kotlin
package com.jobhunter.matching.dto

data class CvSummary(
    val skills: List<String>,
    val yearsTotalExperience: Int?,
    val languages: List<String>,
    val pastRoles: List<String>,
    val education: String?,
    val highlights: String?,   // 1-2 sentences of free-text summary
)
```

- [ ] **Step 2: Create fixtures**

`prompts/cv-summary/sample-1-input.txt`:
```
Shlomi Rahimi
Senior Backend Engineer
7 years of experience in Kotlin, Java, Spring Boot.
Past roles: Driivz (Senior Backend), Wix (Backend Engineer).
Skills: Kotlin, Java, Spring Boot, Postgres, Kafka.
Languages: Hebrew (native), English (fluent).
M.Sc. Computer Science, Tel Aviv University.
```

`prompts/cv-summary/sample-1-expected.json`:
```json
{
  "skills": ["Kotlin","Java","Spring Boot","Postgres","Kafka"],
  "yearsTotalExperience": 7,
  "languages": ["Hebrew","English"],
  "pastRoles": ["Senior Backend at Driivz","Backend Engineer at Wix"],
  "education": "M.Sc. Computer Science, Tel Aviv University",
  "highlights": "Senior backend engineer with 7 years in Kotlin and Spring Boot."
}
```

- [ ] **Step 3: Write the failing test**

```kotlin
package com.jobhunter.matching.prompt

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.jobhunter.processing.client.RecordingLlmClient
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CvSummaryPromptBuilderTest {

    private val mapper = ObjectMapper().registerKotlinModule()
    private val builder = CvSummaryPromptBuilder()

    private fun load(name: String): String =
        javaClass.getResourceAsStream("/prompts/cv-summary/$name")!!
            .bufferedReader().readText()

    @Test
    fun `parses CV summary from sample-1`() {
        val input = load("sample-1-input.txt")
        val expectedJson = load("sample-1-expected.json")

        val llm = RecordingLlmClient()
        llm.record(builder.systemPrompt(), input, expectedJson)

        val result = builder.invoke(llm, input, mapper)
        assertEquals(7, result.yearsTotalExperience)
        assertEquals(true, result.skills.contains("Kotlin"))
        assertEquals(true, result.languages.contains("Hebrew"))
    }
}
```

Note: this test uses `RecordingLlmClient` from `backend/processing/src/test/kotlin/...`. Add a test dependency on processing's test sources, or duplicate the helper. Simplest: copy the small `RecordingLlmClient` class into matching's test sources.

- [ ] **Step 4: Copy `RecordingLlmClient` into `backend/matching/src/test/kotlin/com/jobhunter/matching/client/RecordingLlmClient.kt`**

```kotlin
package com.jobhunter.matching.client

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

Update the test imports to use `com.jobhunter.matching.client.RecordingLlmClient`.

- [ ] **Step 5: Run the test to verify it fails**

```bash
./gradlew :backend:matching:test --tests "com.jobhunter.matching.prompt.CvSummaryPromptBuilderTest"
```

Expected: FAIL.

- [ ] **Step 6: Implement `CvSummaryPromptBuilder`**

```kotlin
package com.jobhunter.matching.prompt

import com.fasterxml.jackson.databind.ObjectMapper
import com.jobhunter.core.client.LlmClient
import com.jobhunter.matching.dto.CvSummary
import org.springframework.stereotype.Component

@Component
class CvSummaryPromptBuilder {

    fun systemPrompt(): String = """
        You extract a structured summary from a CV. Reply with one JSON object only:
        {
          "skills": [string],
          "yearsTotalExperience": int|null,
          "languages": [string],
          "pastRoles": [string],
          "education": string|null,
          "highlights": string|null
        }
        Use null or [] for missing fields. Reply with JSON only.
    """.trimIndent()

    fun invoke(llm: LlmClient, cvText: String, mapper: ObjectMapper): CvSummary {
        val response = llm.chatStructured(system = systemPrompt(), user = cvText)
        return parseOrRetry(llm, cvText, response, mapper)
    }

    private fun parseOrRetry(
        llm: LlmClient, cvText: String, firstResponse: String, mapper: ObjectMapper,
    ): CvSummary = try {
        mapper.readValue(firstResponse, CvSummary::class.java)
    } catch (_: Exception) {
        val retry = llm.chatStructured(
            system = systemPrompt() + "\n\nIMPORTANT: previous response was not valid JSON. Reply with valid JSON only.",
            user = cvText,
        )
        mapper.readValue(retry, CvSummary::class.java)
    }
}
```

- [ ] **Step 7: Run the test to verify it passes**

```bash
./gradlew :backend:matching:test --tests "com.jobhunter.matching.prompt.CvSummaryPromptBuilderTest"
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add backend/matching/src/main/kotlin/com/jobhunter/matching/dto/CvSummary.kt \
        backend/matching/src/main/kotlin/com/jobhunter/matching/prompt/CvSummaryPromptBuilder.kt \
        backend/matching/src/test/resources/prompts/cv-summary \
        backend/matching/src/test/kotlin/com/jobhunter/matching/prompt/CvSummaryPromptBuilderTest.kt \
        backend/matching/src/test/kotlin/com/jobhunter/matching/client/RecordingLlmClient.kt
git commit -m "feat: add CvSummaryPromptBuilder with golden test"
```

---

## Task 4: `CvService` — upload orchestrator + integration test

**Files:**
- Create: `backend/matching/src/main/kotlin/com/jobhunter/matching/dto/CvUploadResponse.kt`
- Create: `backend/matching/src/main/kotlin/com/jobhunter/matching/service/CvService.kt`
- Create: `backend/matching/src/test/kotlin/com/jobhunter/matching/service/CvServiceTest.kt`

The service: parse text → summarize via LLM → embed → save. Mark this CV active and any other previously-active CV inactive (transactional).

- [ ] **Step 1: Create `CvUploadResponse`**

```kotlin
package com.jobhunter.matching.dto

data class CvUploadResponse(
    val id: Long,
    val label: String,
    val parsedTextLength: Int,
    val skills: List<String>,
)
```

- [ ] **Step 2: Write the failing test**

```kotlin
package com.jobhunter.matching.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.jobhunter.core.AbstractRepositoryTest
import com.jobhunter.core.client.EmbeddingClient
import com.jobhunter.core.domain.Cv
import com.jobhunter.core.repository.CvRepository
import com.jobhunter.matching.client.RecordingLlmClient
import com.jobhunter.matching.prompt.CvSummaryPromptBuilder
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.ContextConfiguration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ContextConfiguration(classes = [CvServiceTest.TestBeans::class])
class CvServiceTest : AbstractRepositoryTest() {

    @TestConfiguration
    class TestBeans {
        @Bean fun mapper() = ObjectMapper().registerKotlinModule()
        @Bean fun parseService() = CvParseService()
        @Bean fun summaryBuilder() = CvSummaryPromptBuilder()
    }

    @Autowired lateinit var cvs: CvRepository
    @Autowired lateinit var parseService: CvParseService
    @Autowired lateinit var summaryBuilder: CvSummaryPromptBuilder
    @Autowired lateinit var mapper: ObjectMapper

    @Test
    fun `uploads CV, sets active, deactivates previous active`() {
        // Pre-existing active CV
        cvs.save(Cv("old", "old.pdf", "application/pdf", byteArrayOf(0),
            "old text", FloatArray(1024), null, isActive = true))

        val embeddingClient = mockk<EmbeddingClient>()
        every { embeddingClient.embed(any()) } returns FloatArray(1024) { 0.5f }
        val llm = RecordingLlmClient()
        llm.record(summaryBuilder.systemPrompt(),
            "Sample CV text",
            """{"skills":["Kotlin"],"yearsTotalExperience":5,"languages":["English"],"pastRoles":[],"education":null,"highlights":null}""")

        val service = CvService(cvs, parseService, summaryBuilder, llm, embeddingClient, mapper)

        val pdfBytes = javaClass.getResourceAsStream("/cv-fixtures/sample.pdf")!!.readBytes()
        val response = service.upload(
            label = "current",
            fileName = "cv.pdf",
            mimeType = "application/pdf",
            bytes = pdfBytes,
            // For test determinism: skip Tika and use known text
            overrideText = "Sample CV text",
        )

        assertNotNull(response.id)
        assertEquals("current", response.label)

        // Active CV is the new one
        val active = cvs.findActive()
        assertNotNull(active)
        assertEquals("current", active.label)

        // Old one is no longer active
        val all = cvs.findAll()
        assertEquals(2, all.size)
        assertEquals(1, all.count { it.isActive })
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

```bash
./gradlew :backend:matching:test --tests "com.jobhunter.matching.service.CvServiceTest"
```

Expected: FAIL.

- [ ] **Step 4: Implement `CvService`**

```kotlin
package com.jobhunter.matching.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.jobhunter.core.client.EmbeddingClient
import com.jobhunter.core.client.LlmClient
import com.jobhunter.core.domain.Cv
import com.jobhunter.core.repository.CvRepository
import com.jobhunter.matching.dto.CvUploadResponse
import com.jobhunter.matching.prompt.CvSummaryPromptBuilder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CvService(
    private val cvs: CvRepository,
    private val parseService: CvParseService,
    private val summaryBuilder: CvSummaryPromptBuilder,
    private val llm: LlmClient,
    private val embeddingClient: EmbeddingClient,
    private val mapper: ObjectMapper,
) {

    @Transactional
    fun upload(
        label: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        overrideText: String? = null,  // for tests; production passes null
    ): CvUploadResponse {
        val parsedText = overrideText ?: parseService.extract(bytes, mimeType, fileName)
        require(parsedText.isNotBlank()) { "CV text extraction returned empty" }

        val summary = summaryBuilder.invoke(llm, parsedText, mapper)
        val embedding = embeddingClient.embed(parsedText)
        require(embedding.size == 1024) { "Expected 1024-dim CV embedding, got ${embedding.size}" }

        // Deactivate any current active CV (the partial unique index would block otherwise).
        cvs.findActive()?.let {
            it.isActive = false
            cvs.save(it)
        }

        val saved = cvs.save(Cv(
            label = label,
            fileName = fileName,
            mimeType = mimeType,
            fileBytes = bytes,
            parsedText = parsedText,
            embedding = embedding,
            structuredSummary = mapper.writeValueAsString(summary),
            isActive = true,
        ))

        return CvUploadResponse(
            id = saved.id!!,
            label = saved.label,
            parsedTextLength = parsedText.length,
            skills = summary.skills,
        )
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
./gradlew :backend:matching:test --tests "com.jobhunter.matching.service.CvServiceTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/matching/src/main/kotlin/com/jobhunter/matching/dto/CvUploadResponse.kt \
        backend/matching/src/main/kotlin/com/jobhunter/matching/service/CvService.kt \
        backend/matching/src/test/kotlin/com/jobhunter/matching/service/CvServiceTest.kt
git commit -m "feat: add CvService for upload, parse, summarize, embed, save"
```

---

## Task 5: `CvController` + integration test

**Files:**
- Create: `backend/matching/src/main/kotlin/com/jobhunter/matching/controller/CvController.kt`
- Create: `backend/matching/src/test/kotlin/com/jobhunter/matching/controller/CvControllerTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.jobhunter.matching.controller

import com.jobhunter.matching.dto.CvUploadResponse
import com.jobhunter.matching.service.CvService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class CvControllerTest {

    private val service: CvService = mockk()
    private val cvs = mockk<com.jobhunter.core.repository.CvRepository>()
    private val controller = CvController(service, cvs)
    private val mvc: MockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    @Test
    fun `POST cv accepts multipart upload`() {
        every { service.upload("default", "cv.pdf", "application/pdf", any()) } returns
            CvUploadResponse(id = 1, label = "default", parsedTextLength = 100, skills = listOf("Kotlin"))

        val file = MockMultipartFile("file", "cv.pdf", "application/pdf", "PDFBYTES".toByteArray())

        mvc.perform(multipart("/api/cv").file(file).param("label", "default"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.label").value("default"))

        verify { service.upload("default", "cv.pdf", "application/pdf", any()) }
    }

    @Test
    fun `GET cv lists all CVs`() {
        every { cvs.findAll() } returns emptyList()
        mvc.perform(get("/api/cv"))
            .andExpect(status().isOk)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :backend:matching:test --tests "com.jobhunter.matching.controller.CvControllerTest"
```

Expected: FAIL.

- [ ] **Step 3: Implement `CvController`**

```kotlin
package com.jobhunter.matching.controller

import com.jobhunter.core.repository.CvRepository
import com.jobhunter.matching.dto.CvUploadResponse
import com.jobhunter.matching.service.CvService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/cv")
class CvController(
    private val service: CvService,
    private val cvs: CvRepository,
) {
    @PostMapping
    fun upload(
        @RequestParam(defaultValue = "default") label: String,
        @RequestParam("file") file: MultipartFile,
    ): CvUploadResponse = service.upload(
        label = label,
        fileName = file.originalFilename ?: "cv",
        mimeType = file.contentType ?: "application/octet-stream",
        bytes = file.bytes,
    )

    @GetMapping
    fun list(): List<CvListItem> = cvs.findAll().map {
        CvListItem(
            id = it.id!!,
            label = it.label,
            fileName = it.fileName,
            isActive = it.isActive,
            createdAt = it.createdAt,
        )
    }

    data class CvListItem(
        val id: Long,
        val label: String,
        val fileName: String,
        val isActive: Boolean,
        val createdAt: java.time.Instant,
    )
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :backend:matching:test --tests "com.jobhunter.matching.controller.CvControllerTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/matching/src/main/kotlin/com/jobhunter/matching/controller/CvController.kt \
        backend/matching/src/test/kotlin/com/jobhunter/matching/controller/CvControllerTest.kt
git commit -m "feat: add CvController with multipart upload"
```

---

## Task 6: `MatchPromptBuilder` + golden test

**Files:**
- Create: `backend/matching/src/main/kotlin/com/jobhunter/matching/dto/MatchScoreResult.kt`
- Create: `backend/matching/src/main/kotlin/com/jobhunter/matching/prompt/MatchPromptBuilder.kt`
- Create: `backend/matching/src/test/resources/prompts/match/sample-1-input.json`
- Create: `backend/matching/src/test/resources/prompts/match/sample-1-expected.json`
- Create: `backend/matching/src/test/kotlin/com/jobhunter/matching/prompt/MatchPromptBuilderTest.kt`

- [ ] **Step 1: Create `MatchScoreResult`**

```kotlin
package com.jobhunter.matching.dto

data class MatchScoreResult(
    val score: Int,                      // 0-100
    val strengths: List<String>,
    val gaps: List<String>,
    val summary: String,
)
```

- [ ] **Step 2: Create fixtures**

`prompts/match/sample-1-input.json` (the user-message body the prompt builder formats):
```
CV summary: {"skills":["Kotlin","Spring Boot","Postgres"],"yearsTotalExperience":7,"languages":["Hebrew","English"],"pastRoles":["Senior Backend at Driivz"],"education":"M.Sc. Computer Science","highlights":"Senior backend engineer with 7 years."}

Job:
Title: Backend Engineer (Kotlin)
Requirements: 5+ years backend, Kotlin or Java, Postgres, Hebrew/English
Description: Backend engineer working on microservices platform.
```

`prompts/match/sample-1-expected.json`:
```json
{
  "score": 85,
  "strengths": ["Kotlin","Postgres","exceeds years requirement"],
  "gaps": [],
  "summary": "Strong fit; senior backend with all required tech."
}
```

- [ ] **Step 3: Write the failing test**

```kotlin
package com.jobhunter.matching.prompt

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.jobhunter.matching.client.RecordingLlmClient
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class MatchPromptBuilderTest {

    private val mapper = ObjectMapper().registerKotlinModule()
    private val builder = MatchPromptBuilder()

    private fun load(name: String): String =
        javaClass.getResourceAsStream("/prompts/match/$name")!!
            .bufferedReader().readText()

    @Test
    fun `parses match score from sample-1`() {
        val input = load("sample-1-input.json")
        val expectedJson = load("sample-1-expected.json")

        val llm = RecordingLlmClient()
        llm.record(builder.systemPrompt(), input, expectedJson)

        val result = builder.invokeFromUser(llm, input, mapper)
        assertEquals(85, result.score)
        assertEquals(true, result.strengths.contains("Kotlin"))
    }
}
```

- [ ] **Step 4: Run the test to verify it fails**

```bash
./gradlew :backend:matching:test --tests "com.jobhunter.matching.prompt.MatchPromptBuilderTest"
```

Expected: FAIL.

- [ ] **Step 5: Implement `MatchPromptBuilder`**

```kotlin
package com.jobhunter.matching.prompt

import com.fasterxml.jackson.databind.ObjectMapper
import com.jobhunter.core.client.LlmClient
import com.jobhunter.core.domain.JobPosting
import com.jobhunter.matching.dto.CvSummary
import com.jobhunter.matching.dto.MatchScoreResult
import org.springframework.stereotype.Component

@Component
class MatchPromptBuilder {

    fun systemPrompt(): String = """
        You evaluate fit between a candidate and a job. Output JSON:
        {"score": int 0-100, "strengths": [string], "gaps": [string], "summary": string}
        Score >= 60 means a strong-enough fit to surface to the candidate.
        Reply with JSON only.
    """.trimIndent()

    fun userPrompt(cvSummaryJson: String, posting: JobPosting): String =
        buildString {
            append("CV summary: ").append(cvSummaryJson).append("\n\n")
            append("Job:\n")
            append("Title: ").append(posting.title ?: "(unknown)").append("\n")
            append("Requirements: ").append(posting.requirements ?: "(none specified)").append("\n")
            append("Description: ").append(posting.description ?: "(none specified)").append("\n")
        }

    fun invoke(
        llm: LlmClient,
        cvSummary: CvSummary,
        posting: JobPosting,
        mapper: ObjectMapper,
    ): MatchScoreResult {
        val cvJson = mapper.writeValueAsString(cvSummary)
        return invokeFromUser(llm, userPrompt(cvJson, posting), mapper)
    }

    fun invokeFromUser(llm: LlmClient, user: String, mapper: ObjectMapper): MatchScoreResult {
        val response = llm.chatStructured(system = systemPrompt(), user = user)
        return parseOrRetry(llm, user, response, mapper)
    }

    private fun parseOrRetry(
        llm: LlmClient, user: String, firstResponse: String, mapper: ObjectMapper,
    ): MatchScoreResult = try {
        mapper.readValue(firstResponse, MatchScoreResult::class.java)
    } catch (_: Exception) {
        val retry = llm.chatStructured(
            system = systemPrompt() + "\n\nIMPORTANT: previous response was not valid JSON. Reply with valid JSON only.",
            user = user,
        )
        mapper.readValue(retry, MatchScoreResult::class.java)
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

```bash
./gradlew :backend:matching:test --tests "com.jobhunter.matching.prompt.MatchPromptBuilderTest"
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/matching/src/main/kotlin/com/jobhunter/matching/dto/MatchScoreResult.kt \
        backend/matching/src/main/kotlin/com/jobhunter/matching/prompt/MatchPromptBuilder.kt \
        backend/matching/src/test/resources/prompts/match \
        backend/matching/src/test/kotlin/com/jobhunter/matching/prompt/MatchPromptBuilderTest.kt
git commit -m "feat: add MatchPromptBuilder with golden test"
```

---

## Task 7: `MatchingProperties` + `MatchWorker` + integration test

**Files:**
- Create: `backend/matching/src/main/kotlin/com/jobhunter/matching/config/MatchingProperties.kt`
- Create: `backend/matching/src/main/kotlin/com/jobhunter/matching/worker/MatchWorker.kt`
- Create: `backend/matching/src/test/kotlin/com/jobhunter/matching/worker/MatchWorkerTest.kt`

The worker pulls one `EMBEDDED` row, computes Stage 1 cosine via pgvector, terminal-routes to `IRRELEVANT` if below threshold, otherwise calls Stage 2 LLM. On `llm_score >= threshold`: create `Match` row in `READY_FOR_REVIEW` and advance queue to `MATCHED`. On `llm_score < threshold`: terminal-route queue to `IRRELEVANT` (no Match row).

- [ ] **Step 1: Create `MatchingProperties`**

```kotlin
package com.jobhunter.matching.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("jobhunter.matching")
data class MatchingProperties(
    val cosineThreshold: Double = 0.40,
    val llmScoreThreshold: Int = 60,
)
```

- [ ] **Step 2: Add a cosine query to `PostingEmbeddingRepository`** (in core)

Modify `backend/core/src/main/kotlin/com/jobhunter/core/repository/PostingEmbeddingRepository.kt`:

```kotlin
package com.jobhunter.core.repository

import com.jobhunter.core.domain.PostingEmbedding
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PostingEmbeddingRepository : JpaRepository<PostingEmbedding, Long> {

    /**
     * Computes cosine similarity (1 - cosine distance) between the posting's embedding
     * and the supplied vector. Returns null if no embedding row exists.
     */
    @Query(
        value = "SELECT 1 - (embedding <=> CAST(:vec AS vector)) FROM posting_embedding WHERE job_posting_id = :postingId",
        nativeQuery = true,
    )
    fun cosineToVector(@Param("postingId") postingId: Long, @Param("vec") vec: String): Double?
}
```

The vector arrives as a string `[0.1,0.2,...]` which Postgres casts. We'll provide a small helper to format the array.

- [ ] **Step 3: Write the failing worker test**

```kotlin
package com.jobhunter.matching.worker

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.jobhunter.core.AbstractRepositoryTest
import com.jobhunter.core.domain.*
import com.jobhunter.core.repository.*
import com.jobhunter.matching.client.RecordingLlmClient
import com.jobhunter.matching.config.MatchingProperties
import com.jobhunter.matching.prompt.MatchPromptBuilder
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.ContextConfiguration
import org.springframework.transaction.PlatformTransactionManager
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ContextConfiguration(classes = [MatchWorkerTest.TestBeans::class])
class MatchWorkerTest : AbstractRepositoryTest() {

    @TestConfiguration
    class TestBeans {
        @Bean fun mapper() = ObjectMapper().registerKotlinModule()
        @Bean fun matchPromptBuilder() = MatchPromptBuilder()
    }

    @Autowired lateinit var sources: JobSourceRepository
    @Autowired lateinit var postings: JobPostingRepository
    @Autowired lateinit var queue: ProcessingQueueRepository
    @Autowired lateinit var embeddings: PostingEmbeddingRepository
    @Autowired lateinit var cvs: CvRepository
    @Autowired lateinit var matches: MatchRepository
    @Autowired lateinit var txManager: PlatformTransactionManager
    @Autowired lateinit var mapper: ObjectMapper
    @Autowired lateinit var promptBuilder: MatchPromptBuilder

    private fun seedPosting(): Pair<JobPosting, ProcessingQueueRow> {
        val src = sources.save(JobSource("S-${System.nanoTime()}", SourceType.IMAP, true, "{}"))
        val p = postings.save(JobPosting(
            sourceId = src.id!!, externalId = "E-${System.nanoTime()}",
            rawText = "x", title = "Backend", capturedAt = Instant.now(),
        ))
        embeddings.save(PostingEmbedding(p.id!!, FloatArray(1024) { 0.5f }, "bge-m3"))
        val r = queue.save(ProcessingQueueRow(jobPostingId = p.id!!, state = QueueState.EMBEDDED))
        return p to r
    }

    private fun seedActiveCv() {
        cvs.save(Cv("default", "cv.pdf", "application/pdf", byteArrayOf(0),
            "CV text", FloatArray(1024) { 0.5f },
            structuredSummary = """{"skills":[],"yearsTotalExperience":null,"languages":[],"pastRoles":[],"education":null,"highlights":null}""",
            isActive = true))
    }

    @Test
    fun `low cosine routes to IRRELEVANT, no match row`() {
        seedActiveCv()
        // Override: change CV embedding to be opposite of posting embedding (cosine ≈ 0)
        cvs.findActive()!!.let {
            it.embedding = FloatArray(1024) { -0.5f }
            cvs.save(it)
        }
        val (_, queueRow) = seedPosting()

        val llm = RecordingLlmClient()
        val props = MatchingProperties(cosineThreshold = 0.95, llmScoreThreshold = 60)
        val worker = MatchWorker(queue, postings, embeddings, cvs, matches, txManager, llm, promptBuilder, mapper, props)
        worker.runOnce()

        assertEquals(QueueState.IRRELEVANT, queue.findById(queueRow.id!!).get().state)
        assertEquals(0L, matches.count())
    }

    @Test
    fun `high cosine and high llm score creates Match in READY_FOR_REVIEW`() {
        seedActiveCv()
        val (post, queueRow) = seedPosting()

        val llm = RecordingLlmClient()
        // Match prompt fires; we record-by-user so we don't need exact prompt text
        llm.recordByUser(
            user = io.mockk.match<String> { it.contains("Backend") }.toString(),  // unused — see below
            response = "",
        )
        // Simpler: stub everything to a high-score response
        val recording = llm
        // Use a wildcard approach — copy responses for any user content
        val anyUser = io.mockk.mockk<com.jobhunter.core.client.LlmClient>()
        io.mockk.every { anyUser.chatStructured(any(), any()) } returns
            """{"score":85,"strengths":["Kotlin"],"gaps":[],"summary":"good fit"}"""
        io.mockk.every { anyUser.chat(any(), any()) } returns
            """{"score":85,"strengths":["Kotlin"],"gaps":[],"summary":"good fit"}"""

        val props = MatchingProperties(cosineThreshold = 0.0, llmScoreThreshold = 60)
        val worker = MatchWorker(queue, postings, embeddings, cvs, matches, txManager,
            anyUser, promptBuilder, mapper, props)
        worker.runOnce()

        assertEquals(QueueState.MATCHED, queue.findById(queueRow.id!!).get().state)
        assertEquals(1L, matches.count())
        val m = matches.findAll().first()
        assertEquals(MatchState.READY_FOR_REVIEW, m.state)
        assertEquals(85, m.llmScore)
    }

    @Test
    fun `high cosine but low llm score routes to IRRELEVANT`() {
        seedActiveCv()
        val (_, queueRow) = seedPosting()

        val anyUser = io.mockk.mockk<com.jobhunter.core.client.LlmClient>()
        io.mockk.every { anyUser.chatStructured(any(), any()) } returns
            """{"score":30,"strengths":[],"gaps":["years"],"summary":"weak"}"""
        io.mockk.every { anyUser.chat(any(), any()) } returns
            """{"score":30,"strengths":[],"gaps":["years"],"summary":"weak"}"""

        val props = MatchingProperties(cosineThreshold = 0.0, llmScoreThreshold = 60)
        MatchWorker(queue, postings, embeddings, cvs, matches, txManager, anyUser, promptBuilder, mapper, props).runOnce()

        assertEquals(QueueState.IRRELEVANT, queue.findById(queueRow.id!!).get().state)
        assertEquals(0L, matches.count())
    }
}
```

- [ ] **Step 4: Run the test to verify it fails**

```bash
./gradlew :backend:matching:test --tests "com.jobhunter.matching.worker.MatchWorkerTest"
```

Expected: FAIL.

- [ ] **Step 5: Implement `MatchWorker`**

```kotlin
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

        // Stage 1 — cosine
        val cvVec = formatVector(cv.embedding)
        val cosine = embeddings.cosineToVector(jobPostingId, cvVec)
            ?: throw IllegalStateException("No embedding for posting $jobPostingId")

        if (cosine < props.cosineThreshold) {
            terminate(jobPostingId, QueueState.IRRELEVANT)
            return
        }

        // Stage 2 — LLM rerank
        val cvSummary: CvSummary = mapper.readValue(
            cv.structuredSummary ?: "{}",
            CvSummary::class.java,
        )
        val score = promptBuilder.invoke(llm, cvSummary, posting, mapper)

        if (score.score < props.llmScoreThreshold) {
            terminate(jobPostingId, QueueState.IRRELEVANT)
            return
        }

        matches.save(Match(
            jobPostingId = jobPostingId,
            cvId = cv.id!!,
            cosineSimilarity = cosine,
            llmScore = score.score,
            llmReasoning = mapper.writeValueAsString(score),
            state = MatchState.READY_FOR_REVIEW,
        ))
        // base class advances queue to MATCHED
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
```

- [ ] **Step 6: Run the test to verify it passes**

```bash
./gradlew :backend:matching:test --tests "com.jobhunter.matching.worker.MatchWorkerTest"
```

Expected: PASS — all three tests.

- [ ] **Step 7: Commit**

```bash
git add backend/core/src/main/kotlin/com/jobhunter/core/repository/PostingEmbeddingRepository.kt \
        backend/matching/src/main/kotlin/com/jobhunter/matching/config/MatchingProperties.kt \
        backend/matching/src/main/kotlin/com/jobhunter/matching/worker/MatchWorker.kt \
        backend/matching/src/test/kotlin/com/jobhunter/matching/worker/MatchWorkerTest.kt
git commit -m "feat: add MatchWorker with two-stage cosine and LLM scoring"
```

---

## Task 8: `MatchService` + `MatchController` + integration test

**Files:**
- Create: `backend/matching/src/main/kotlin/com/jobhunter/matching/dto/MatchView.kt`
- Create: `backend/matching/src/main/kotlin/com/jobhunter/matching/service/MatchService.kt`
- Create: `backend/matching/src/main/kotlin/com/jobhunter/matching/controller/MatchController.kt`
- Create: `backend/matching/src/test/kotlin/com/jobhunter/matching/controller/MatchControllerTest.kt`

The read-side API for the React UI: list `READY_FOR_REVIEW` matches, get one match with full posting detail.

- [ ] **Step 1: Create `MatchView`**

```kotlin
package com.jobhunter.matching.dto

import com.jobhunter.core.domain.MatchState
import java.time.Instant

data class MatchView(
    val id: Long,
    val state: MatchState,
    val llmScore: Int?,
    val cosineSimilarity: Double,
    val reasoning: String?,
    val posting: PostingView,
    val createdAt: Instant,
)

data class PostingView(
    val id: Long,
    val title: String?,
    val company: String?,
    val location: String?,
    val isRemote: Boolean?,
    val language: String?,
    val description: String?,
    val requirements: String?,
    val contactEmail: String?,
    val applyUrl: String?,
    val sourceUrl: String?,
)
```

- [ ] **Step 2: Implement `MatchService`**

```kotlin
package com.jobhunter.matching.service

import com.jobhunter.core.domain.MatchState
import com.jobhunter.core.repository.JobPostingRepository
import com.jobhunter.core.repository.MatchRepository
import com.jobhunter.matching.dto.MatchView
import com.jobhunter.matching.dto.PostingView
import org.springframework.stereotype.Service

@Service
class MatchService(
    private val matches: MatchRepository,
    private val postings: JobPostingRepository,
) {

    fun listReady(): List<MatchView> =
        matches.findReadyForReview().mapNotNull { toView(it) }

    fun get(id: Long): MatchView? =
        matches.findById(id).map { toView(it) }.orElse(null)

    private fun toView(m: com.jobhunter.core.domain.Match): MatchView? {
        val p = postings.findById(m.jobPostingId).orElse(null) ?: return null
        return MatchView(
            id = m.id!!,
            state = m.state,
            llmScore = m.llmScore,
            cosineSimilarity = m.cosineSimilarity,
            reasoning = m.llmReasoning,
            posting = PostingView(
                id = p.id!!,
                title = p.title,
                company = p.company,
                location = p.location,
                isRemote = p.isRemote,
                language = p.language,
                description = p.description,
                requirements = p.requirements,
                contactEmail = p.contactEmail,
                applyUrl = p.applyUrl,
                sourceUrl = p.sourceUrl,
            ),
            createdAt = m.createdAt,
        )
    }
}
```

- [ ] **Step 3: Implement `MatchController`**

```kotlin
package com.jobhunter.matching.controller

import com.jobhunter.matching.dto.MatchView
import com.jobhunter.matching.service.MatchService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/matches")
class MatchController(private val service: MatchService) {

    @GetMapping
    fun list(): List<MatchView> = service.listReady()

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): ResponseEntity<MatchView> =
        service.get(id)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()
}
```

- [ ] **Step 4: Write controller test**

```kotlin
package com.jobhunter.matching.controller

import com.jobhunter.core.domain.MatchState
import com.jobhunter.matching.dto.MatchView
import com.jobhunter.matching.dto.PostingView
import com.jobhunter.matching.service.MatchService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant

class MatchControllerTest {

    private val service: MatchService = mockk()
    private val controller = MatchController(service)
    private val mvc: MockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    @Test
    fun `GET matches returns ready list`() {
        every { service.listReady() } returns listOf(MatchView(
            id = 1, state = MatchState.READY_FOR_REVIEW, llmScore = 85, cosineSimilarity = 0.8,
            reasoning = null,
            posting = PostingView(1, "Backend", "Acme", "TLV", false, "en",
                null, null, "jobs@acme.com", null, null),
            createdAt = Instant.now(),
        ))
        mvc.perform(get("/api/matches"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].llmScore").value(85))
            .andExpect(jsonPath("$[0].posting.contactEmail").value("jobs@acme.com"))
    }

    @Test
    fun `GET matches by id returns 404 when missing`() {
        every { service.get(99) } returns null
        mvc.perform(get("/api/matches/99"))
            .andExpect(status().isNotFound)
    }
}
```

- [ ] **Step 5: Run the controller test to verify it passes**

```bash
./gradlew :backend:matching:test --tests "com.jobhunter.matching.controller.MatchControllerTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/matching/src/main/kotlin/com/jobhunter/matching/dto/MatchView.kt \
        backend/matching/src/main/kotlin/com/jobhunter/matching/service/MatchService.kt \
        backend/matching/src/main/kotlin/com/jobhunter/matching/controller/MatchController.kt \
        backend/matching/src/test/kotlin/com/jobhunter/matching/controller/MatchControllerTest.kt
git commit -m "feat: add MatchService and MatchController for read-side"
```

---

## Task 9: Final verification

- [ ] **Step 1: Run the full test suite**

```bash
./gradlew test
```

Expected: ALL tests pass across all four modules.

- [ ] **Step 2: Manual smoke test (optional)**

```bash
docker compose up -d postgres ollama
./gradlew :backend:app:bootRun
# In another terminal:
curl -X POST -F "file=@/path/to/your/cv.pdf" -F "label=default" http://localhost:8080/api/cv
curl http://localhost:8080/api/cv
curl http://localhost:8080/api/matches
```

- [ ] **Step 3: Update README**

Add section:

```markdown
## Uploading your CV

```bash
curl -X POST -F "file=@cv.pdf" -F "label=default" http://localhost:8080/api/cv
```

The first upload becomes the active CV. Each subsequent upload deactivates the previous active CV (history is preserved).
```

- [ ] **Step 4: Commit and tag**

```bash
git add README.md
git commit -m "docs: add CV upload instructions"
git tag plan-4-complete
```

---

## End of Plan 4

**At this point:**

- [x] CV upload accepts PDF/DOCX, extracts text via Tika, embeds via bge-m3, summarizes via LLM, sets active.
- [x] `MatchWorker` consumes `EMBEDDED` rows, two-stage scores against the active CV, creates `Match` rows in `READY_FOR_REVIEW` for results above thresholds, terminal-routes others to `IRRELEVANT`.
- [x] `GET /api/matches` returns ready matches sorted by score (descending).
- [x] `GET /api/matches/{id}` returns full detail with the posting.
- [x] All tests pass.

**Next plan: Plan 5 — Delivery** (cover-letter draft generation, SMTP send, email_send_record audit trail).
