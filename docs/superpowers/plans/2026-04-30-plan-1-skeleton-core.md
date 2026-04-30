# Plan 1 — Skeleton, Core, and Schema

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bootstrap the Gradle multi-module project, wire up Postgres + Ollama via docker-compose, create the full database schema (Flyway V1), define all JPA entities + repositories, and build the shared infrastructure (LlmClient, EmbeddingClient, QueueWorker abstract, queue notifier, health indicators) that downstream plans will consume. End state: app boots from IntelliJ, `/actuator/health` is green, all repository integration tests pass against a Testcontainers Postgres.

**Architecture:** Single Gradle multi-module project (`backend/core`, `backend/app`). `core` holds the entire data model, queue framework, and external-system clients. `app` is the Spring Boot bootstrap with health endpoints. Postgres + Ollama run in docker-compose; the Spring app runs from IntelliJ in dev. Flyway manages schema; pgvector handles embeddings. No business logic is implemented in this plan — only the substrate.

**Tech Stack:** Kotlin 2.0.21, Spring Boot 3.4.1, Spring AI 1.0.0, Java 21 toolchain, Postgres 16 + pgvector, Flyway, Spring Data JPA, Testcontainers, MockK, JUnit 5, Gradle 8.x (Kotlin DSL).

**Reference spec:** `docs/superpowers/specs/2026-04-30-ai-job-hunter-design.md`

---

## File Structure (created or finalized in this plan)

**Root:**
- `settings.gradle.kts` — multi-module declaration
- `build.gradle.kts` — root plugin versions, repositories, common config
- `gradle.properties` — Gradle build settings
- `.gitignore` — exclude build artifacts, IntelliJ, secrets
- `README.md` — project intro, dev setup
- `docker-compose.yml` — Postgres (pgvector image) + Ollama services

**`backend/core/`** (the substrate — depended upon by all feature modules):
- `build.gradle.kts` — module dependencies (Spring Data JPA, Spring AI, pgvector, Flyway)
- `src/main/resources/db/migration/V1__init.sql` — full schema: extensions, enums, tables, indexes
- `src/main/kotlin/com/jobhunter/core/jpa/PgVectorType.kt` — Hibernate UserType for `vector(N)`
- `src/main/kotlin/com/jobhunter/core/domain/Category.kt` — Postgres enum mapping
- `src/main/kotlin/com/jobhunter/core/domain/JobSource.kt` — `@Entity` + `SourceType` enum
- `src/main/kotlin/com/jobhunter/core/domain/JobPosting.kt` — `@Entity` (the central row)
- `src/main/kotlin/com/jobhunter/core/domain/PostingEmbedding.kt` — `@Entity` (1:1 vector)
- `src/main/kotlin/com/jobhunter/core/domain/ProcessingQueueRow.kt` — `@Entity` + `QueueState`
- `src/main/kotlin/com/jobhunter/core/domain/Cv.kt` — `@Entity` (BYTEA + vector + JSONB)
- `src/main/kotlin/com/jobhunter/core/domain/Match.kt` — `@Entity` + `MatchState`
- `src/main/kotlin/com/jobhunter/core/domain/EmailSendRecord.kt` — `@Entity`
- `src/main/kotlin/com/jobhunter/core/repository/*.kt` — one `JpaRepository` per entity
- `src/main/kotlin/com/jobhunter/core/client/LlmClient.kt` — interface + structured-output method
- `src/main/kotlin/com/jobhunter/core/client/OllamaLlmClient.kt` — Spring AI `ChatModel` wrapper
- `src/main/kotlin/com/jobhunter/core/client/EmbeddingClient.kt` — interface
- `src/main/kotlin/com/jobhunter/core/client/OllamaEmbeddingClient.kt` — Spring AI `EmbeddingModel` wrapper
- `src/main/kotlin/com/jobhunter/core/worker/QueueWorker.kt` — abstract base class for stage workers
- `src/main/kotlin/com/jobhunter/core/worker/QueueNotifier.kt` — Postgres `LISTEN/NOTIFY` wrapper
- `src/test/kotlin/com/jobhunter/core/AbstractRepositoryTest.kt` — Testcontainers base
- `src/test/kotlin/com/jobhunter/core/repository/*Test.kt` — one per repo
- `src/test/kotlin/com/jobhunter/core/client/*Test.kt` — LLM client tests with mocks
- `src/test/kotlin/com/jobhunter/core/worker/*Test.kt` — queue/worker tests

**`backend/app/`** (the bootstrap):
- `build.gradle.kts` — depends on `core`, applies Spring Boot plugin
- `src/main/kotlin/com/jobhunter/app/JobHunterApplication.kt` — `@SpringBootApplication`
- `src/main/kotlin/com/jobhunter/app/health/OllamaHealthIndicator.kt` — Actuator indicator
- `src/main/resources/application.yml` — base config
- `src/main/resources/application-local.yml.example` — template for secrets (gitignored real one)
- `src/test/kotlin/com/jobhunter/app/SmokeTest.kt` — full-app boot test

---

## Task 1: Project init — Gradle multi-module skeleton

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `.gitignore`
- Create: `README.md`

This is configuration; no failing test. Verification = `./gradlew tasks` runs successfully.

- [ ] **Step 1: Create `settings.gradle.kts`**

```kotlin
rootProject.name = "ai-job-hunter"

include("backend:core", "backend:app")

project(":backend:core").projectDir = file("backend/core")
project(":backend:app").projectDir = file("backend/app")
```

- [ ] **Step 2: Create root `build.gradle.kts`**

```kotlin
plugins {
    kotlin("jvm") version "2.0.21" apply false
    kotlin("plugin.spring") version "2.0.21" apply false
    kotlin("plugin.jpa") version "2.0.21" apply false
    id("org.springframework.boot") version "3.4.1" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
    group = "com.jobhunter"
    version = "0.1.0-SNAPSHOT"
    repositories { mavenCentral() }
}

subprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
            freeCompilerArgs.add("-Xjsr305=strict")
        }
    }
    tasks.withType<Test>().configureEach { useJUnitPlatform() }
}
```

- [ ] **Step 3: Create `gradle.properties`**

```properties
org.gradle.parallel=true
org.gradle.caching=true
kotlin.code.style=official
```

- [ ] **Step 4: Create `.gitignore`**

```gitignore
.gradle/
build/
.idea/
*.iml
*.iws
out/
.DS_Store
logs/
*.log
backend/app/src/main/resources/application-local.yml
backend/*/build/
```

- [ ] **Step 5: Create minimal `README.md`**

```markdown
# AI Job Hunter

Personal job-search and CV-matching system. See `docs/superpowers/specs/2026-04-30-ai-job-hunter-design.md` for full design.

## Dev setup

1. `docker compose up -d postgres ollama`
2. First-time only: `docker exec -it jobhunter-ollama ollama pull aya-expanse:32b` then `bge-m3`
3. Open in IntelliJ; run `JobHunterApplication.kt`
4. Browse `http://localhost:8080/actuator/health`

## Stack

Kotlin · Spring Boot · Postgres + pgvector · Ollama (local LLM) · React (later plan)
```

- [ ] **Step 6: Initialize git and verify Gradle**

```bash
git init
./gradlew tasks
```

Expected: Gradle prints task list without errors. (Modules `:backend:core` and `:backend:app` won't have tasks yet — that's fine.)

- [ ] **Step 7: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties .gitignore README.md
git commit -m "chore: initialize Gradle multi-module project"
```

---

## Task 2: docker-compose for Postgres and Ollama

**Files:**
- Create: `docker-compose.yml`

Verification = `docker compose up -d postgres ollama` brings up healthy containers.

- [ ] **Step 1: Create `docker-compose.yml`**

```yaml
services:
  postgres:
    image: pgvector/pgvector:pg16
    container_name: jobhunter-postgres
    environment:
      POSTGRES_USER: jobhunter
      POSTGRES_PASSWORD: jobhunter
      POSTGRES_DB: jobhunter
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U jobhunter -d jobhunter"]
      interval: 5s
      timeout: 3s
      retries: 10

  ollama:
    image: ollama/ollama:latest
    container_name: jobhunter-ollama
    ports:
      - "11434:11434"
    volumes:
      - ollama-data:/root/.ollama
    healthcheck:
      test: ["CMD", "ollama", "list"]
      interval: 30s
      timeout: 5s
      retries: 5

volumes:
  postgres-data:
  ollama-data:
```

- [ ] **Step 2: Bring up the sidecars and verify**

```bash
docker compose up -d postgres ollama
docker compose ps
```

Expected: both services in `running (healthy)` state. Postgres may take ~10s; Ollama is healthy as soon as the binary responds. If Ollama health check is `starting` for >60s, that is fine — first run is slow.

- [ ] **Step 3: Sanity-check Postgres connectivity**

```bash
docker exec jobhunter-postgres psql -U jobhunter -d jobhunter -c "SELECT version();"
```

Expected: prints PostgreSQL 16.x.

- [ ] **Step 4: Pull required models (one-time, can run in background)**

```bash
docker exec jobhunter-ollama ollama pull bge-m3
docker exec jobhunter-ollama ollama pull aya-expanse:32b
```

Expected: progress bars; final message `success`. The 32B model is ~19 GB and will take a while; you can defer this step until Plan 3 if you want to keep moving.

- [ ] **Step 5: Commit**

```bash
git add docker-compose.yml
git commit -m "chore: add docker-compose for postgres + ollama"
```

---

## Task 3: Bootstrap `backend/core` module

**Files:**
- Create: `backend/core/build.gradle.kts`
- Create: `backend/core/src/main/kotlin/com/jobhunter/core/.gitkeep`
- Create: `backend/core/src/main/resources/.gitkeep`

- [ ] **Step 1: Create `backend/core/build.gradle.kts`**

```kotlin
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.4.1")
        mavenBom("org.springframework.ai:spring-ai-bom:1.0.0")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.ai:spring-ai-ollama-spring-boot-starter")
    implementation("org.springframework.retry:spring-retry")
    implementation("org.springframework:spring-aspects")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.postgresql:postgresql")
    implementation("com.pgvector:pgvector:0.1.4")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:postgresql:1.20.4")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
    testImplementation("io.mockk:mockk:1.13.13")
}

kotlin { jvmToolchain(21) }

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}
```

- [ ] **Step 2: Create empty package directories**

```bash
mkdir -p backend/core/src/main/kotlin/com/jobhunter/core
mkdir -p backend/core/src/main/resources/db/migration
mkdir -p backend/core/src/test/kotlin/com/jobhunter/core
touch backend/core/src/main/kotlin/com/jobhunter/core/.gitkeep
touch backend/core/src/main/resources/.gitkeep
```

- [ ] **Step 3: Verify the module builds**

```bash
./gradlew :backend:core:compileKotlin
```

Expected: `BUILD SUCCESSFUL`. (Nothing to compile yet, just confirms the module config is valid.)

- [ ] **Step 4: Commit**

```bash
git add backend/core/
git commit -m "chore: bootstrap core module with dependencies"
```

---

## Task 4: Bootstrap `backend/app` module with Spring Boot main

**Files:**
- Create: `backend/app/build.gradle.kts`
- Create: `backend/app/src/main/kotlin/com/jobhunter/app/JobHunterApplication.kt`
- Create: `backend/app/src/main/resources/application.yml`
- Create: `backend/app/src/main/resources/application-local.yml.example`

- [ ] **Step 1: Create `backend/app/build.gradle.kts`**

```kotlin
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
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
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:postgresql:1.20.4")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
}

kotlin { jvmToolchain(21) }

springBoot {
    mainClass.set("com.jobhunter.app.JobHunterApplicationKt")
}
```

- [ ] **Step 2: Create `JobHunterApplication.kt`**

```kotlin
package com.jobhunter.app

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.retry.annotation.EnableRetry

@SpringBootApplication
@ComponentScan(basePackages = ["com.jobhunter"])
@EntityScan("com.jobhunter.core.domain")
@EnableJpaRepositories("com.jobhunter.core.repository")
@EnableScheduling
@EnableAsync
@EnableRetry
class JobHunterApplication

fun main(args: Array<String>) {
    runApplication<JobHunterApplication>(*args)
}
```

- [ ] **Step 3: Create `application.yml`**

```yaml
spring:
  application:
    name: ai-job-hunter
  profiles:
    active: local
  datasource:
    url: jdbc:postgresql://localhost:5432/jobhunter
    username: jobhunter
    password: jobhunter
    hikari:
      maximum-pool-size: 10
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        jdbc.lob.non_contextual_creation: true
        format_sql: true
    open-in-view: false
  flyway:
    enabled: true
    locations: classpath:db/migration
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: aya-expanse:32b
          temperature: 0.2
      embedding:
        options:
          model: bge-m3

server:
  port: 8080

management:
  endpoints:
    web:
      exposure:
        include: health, info
  endpoint:
    health:
      show-details: always

jobhunter:
  monitored-categories:
    - SOFTWARE_BACKEND
    - SOFTWARE_FULLSTACK
    - DEVOPS
    - SRE
    - PLATFORM
  matching:
    cosine-threshold: 0.40
    llm-score-threshold: 60

logging:
  level:
    com.jobhunter: INFO
    org.hibernate.SQL: WARN
```

- [ ] **Step 4: Create `application-local.yml.example`**

```yaml
# Copy this file to application-local.yml and fill in real values.
# application-local.yml is gitignored.

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
  imap:
    host: imap.gmail.com
    port: 993
    username: your.email@gmail.com
    password: your-google-app-password
```

- [ ] **Step 5: Verify the app boots (with sidecars up)**

```bash
docker compose up -d postgres ollama
./gradlew :backend:app:bootRun --args='--spring.flyway.enabled=false --spring.jpa.hibernate.ddl-auto=none'
```

The `--spring.flyway.enabled=false` and `--spring.jpa.hibernate.ddl-auto=none` flags let the app boot before we have a migration. Press Ctrl-C after you see `Started JobHunterApplication`.

Expected: log line `Started JobHunterApplication in <N> seconds`. If you see an Ollama connection warning, that's fine for now (we add the health indicator later).

- [ ] **Step 6: Commit**

```bash
git add backend/app/
git commit -m "chore: bootstrap app module with Spring Boot main"
```

---

## Task 5: Flyway V1 migration — full schema

**Files:**
- Create: `backend/core/src/main/resources/db/migration/V1__init.sql`

This is the canonical schema for the entire system. All entities in subsequent tasks map to tables defined here. Subsequent plans add later migrations only for changes (V2, V3...).

- [ ] **Step 1: Write a failing boot test**

Create `backend/app/src/test/kotlin/com/jobhunter/app/SchemaMigrationTest.kt`:

```kotlin
package com.jobhunter.app

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals

@SpringBootTest
@Testcontainers
class SchemaMigrationTest {
    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("pgvector/pgvector:pg16")
            .withDatabaseName("jobhunter")
            .withUsername("jobhunter")
            .withPassword("jobhunter")

        @DynamicPropertySource
        @JvmStatic
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.ai.ollama.base-url") { "http://disabled" }
        }
    }

    @Autowired lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `migration creates all expected tables`() {
        val tables = jdbcTemplate.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name",
            String::class.java,
        )
        val expected = listOf(
            "cv",
            "email_send_record",
            "flyway_schema_history",
            "job_posting",
            "job_source",
            "match",
            "posting_embedding",
            "processing_queue",
        )
        assertEquals(expected, tables)
    }

    @Test
    fun `pgvector extension is installed`() {
        val count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM pg_extension WHERE extname = 'vector'",
            Int::class.java,
        )
        assertEquals(1, count)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :backend:app:test --tests "com.jobhunter.app.SchemaMigrationTest"
```

Expected: FAIL because no V1 migration exists yet (Flyway will report no migrations found, or app boot fails because `ddl-auto: validate` finds nothing to validate against).

- [ ] **Step 3: Write the V1 migration**

Create `backend/core/src/main/resources/db/migration/V1__init.sql`:

```sql
-- Extensions
CREATE EXTENSION IF NOT EXISTS vector;

-- Enums
CREATE TYPE category AS ENUM (
    'SOFTWARE_BACKEND',
    'SOFTWARE_FULLSTACK',
    'SOFTWARE_FRONTEND',
    'DEVOPS',
    'SRE',
    'PLATFORM',
    'DATA_ENGINEERING',
    'DATA_SCIENCE',
    'EMBEDDED',
    'MOBILE',
    'QA_AUTOMATION',
    'SECURITY',
    'PRODUCT_MANAGEMENT',
    'DESIGN',
    'HUMAN_RESOURCES',
    'MARKETING',
    'SALES',
    'CUSTOMER_SUCCESS',
    'FINANCE',
    'LEGAL',
    'OPERATIONS',
    'ADMIN',
    'CONSTRUCTION',
    'HEALTHCARE',
    'EDUCATION',
    'OTHER'
);

-- job_source
CREATE TABLE job_source (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    type VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    config JSONB NOT NULL DEFAULT '{}'::jsonb,
    last_run_at TIMESTAMPTZ,
    last_run_status VARCHAR(20),
    last_run_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- job_posting
CREATE TABLE job_posting (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT NOT NULL REFERENCES job_source(id),
    external_id VARCHAR(255) NOT NULL,
    source_url TEXT,
    raw_text TEXT NOT NULL,
    raw_html TEXT,
    title VARCHAR(500),
    company VARCHAR(255),
    location VARCHAR(255),
    is_remote BOOLEAN,
    language CHAR(2),
    contact_email VARCHAR(255),
    apply_url TEXT,
    description TEXT,
    requirements TEXT,
    salary_text VARCHAR(255),
    categories category[],
    posted_at TIMESTAMPTZ,
    captured_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (source_id, external_id)
);

CREATE INDEX idx_posting_categories ON job_posting USING GIN (categories);
CREATE INDEX idx_posting_captured_at ON job_posting (captured_at DESC);
CREATE INDEX idx_posting_contact_email ON job_posting (contact_email)
    WHERE contact_email IS NOT NULL;

-- posting_embedding
CREATE TABLE posting_embedding (
    job_posting_id BIGINT PRIMARY KEY REFERENCES job_posting(id) ON DELETE CASCADE,
    embedding vector(1024) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_posting_embedding_hnsw ON posting_embedding
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- cv
CREATE TABLE cv (
    id BIGSERIAL PRIMARY KEY,
    label VARCHAR(100) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_bytes BYTEA NOT NULL,
    parsed_text TEXT NOT NULL,
    embedding vector(1024) NOT NULL,
    structured_summary JSONB,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX cv_one_active ON cv (is_active) WHERE is_active = TRUE;

-- processing_queue
CREATE TABLE processing_queue (
    id BIGSERIAL PRIMARY KEY,
    job_posting_id BIGINT NOT NULL REFERENCES job_posting(id) ON DELETE CASCADE,
    state VARCHAR(30) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    last_error TEXT,
    next_attempt_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_queue_state_next ON processing_queue (state, next_attempt_at);

-- match
CREATE TABLE match (
    id BIGSERIAL PRIMARY KEY,
    job_posting_id BIGINT NOT NULL REFERENCES job_posting(id) ON DELETE CASCADE,
    cv_id BIGINT NOT NULL REFERENCES cv(id),
    cosine_similarity DOUBLE PRECISION NOT NULL,
    llm_score INT,
    llm_reasoning JSONB,
    state VARCHAR(30) NOT NULL,
    draft_subject VARCHAR(500),
    draft_body TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (job_posting_id, cv_id)
);

CREATE INDEX idx_match_state_score ON match (state, llm_score DESC NULLS LAST);

-- email_send_record
CREATE TABLE email_send_record (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL UNIQUE REFERENCES match(id),
    cv_id BIGINT NOT NULL REFERENCES cv(id),
    to_address VARCHAR(255) NOT NULL,
    subject VARCHAR(500) NOT NULL,
    body TEXT NOT NULL,
    attachment_filename VARCHAR(255),
    sent_at TIMESTAMPTZ NOT NULL,
    smtp_message_id VARCHAR(255),
    status VARCHAR(20) NOT NULL,
    failure_reason TEXT
);
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :backend:app:test --tests "com.jobhunter.app.SchemaMigrationTest"
```

Expected: PASS — both `migration creates all expected tables` and `pgvector extension is installed`.

- [ ] **Step 5: Commit**

```bash
git add backend/core/src/main/resources/db/migration/V1__init.sql \
        backend/app/src/test/kotlin/com/jobhunter/app/SchemaMigrationTest.kt
git commit -m "feat: add V1 schema migration with full data model"
```

---

## Task 6: pgvector Hibernate UserType

**Files:**
- Create: `backend/core/src/main/kotlin/com/jobhunter/core/jpa/PgVectorType.kt`
- Create: `backend/core/src/test/kotlin/com/jobhunter/core/jpa/PgVectorTypeTest.kt`

JPA needs a custom type to map Postgres `vector(N)` ↔ Kotlin `FloatArray`. The `pgvector-java` library provides the JDBC plumbing; this Hibernate UserType bridges it.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.jobhunter.core.jpa

import org.junit.jupiter.api.Test
import java.sql.PreparedStatement
import java.sql.ResultSet
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import com.pgvector.PGvector
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PgVectorTypeTest {
    private val type = PgVectorType()

    @Test
    fun `nullSafeGet returns null for null column`() {
        val rs = mockk<ResultSet>()
        every { rs.getObject(1) } returns null
        every { rs.wasNull() } returns true
        assertNull(type.nullSafeGet(rs, 1, mockk(relaxed = true), null))
    }

    @Test
    fun `nullSafeGet maps PGvector to FloatArray`() {
        val rs = mockk<ResultSet>()
        every { rs.getObject(1) } returns PGvector(floatArrayOf(0.1f, 0.2f, 0.3f))
        every { rs.wasNull() } returns false
        val result = type.nullSafeGet(rs, 1, mockk(relaxed = true), null)
        assertEquals(listOf(0.1f, 0.2f, 0.3f), (result as FloatArray).toList())
    }

    @Test
    fun `nullSafeSet writes PGvector for non-null FloatArray`() {
        val ps = mockk<PreparedStatement>(relaxed = true)
        val value = floatArrayOf(0.5f, 0.6f)
        type.nullSafeSet(ps, value, 1, mockk(relaxed = true))
        verify { ps.setObject(1, match<PGvector> { it.toArray().toList() == listOf(0.5f, 0.6f) }) }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :backend:core:test --tests "com.jobhunter.core.jpa.PgVectorTypeTest"
```

Expected: FAIL — `PgVectorType` does not exist.

- [ ] **Step 3: Implement `PgVectorType`**

```kotlin
package com.jobhunter.core.jpa

import com.pgvector.PGvector
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.usertype.UserType
import java.io.Serializable
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types

class PgVectorType : UserType<FloatArray> {
    override fun getSqlType(): Int = Types.OTHER
    override fun returnedClass(): Class<FloatArray> = FloatArray::class.java
    override fun equals(x: FloatArray?, y: FloatArray?): Boolean = x.contentEquals(y)
    override fun hashCode(x: FloatArray?): Int = x?.contentHashCode() ?: 0

    override fun nullSafeGet(
        rs: ResultSet,
        position: Int,
        session: SharedSessionContractImplementor?,
        owner: Any?,
    ): FloatArray? {
        val obj = rs.getObject(position)
        if (rs.wasNull() || obj == null) return null
        return when (obj) {
            is PGvector -> obj.toArray()
            else -> PGvector(obj.toString()).toArray()
        }
    }

    override fun nullSafeSet(
        st: PreparedStatement,
        value: FloatArray?,
        index: Int,
        session: SharedSessionContractImplementor?,
    ) {
        if (value == null) {
            st.setNull(index, Types.OTHER)
        } else {
            st.setObject(index, PGvector(value))
        }
    }

    override fun deepCopy(value: FloatArray?): FloatArray? = value?.copyOf()
    override fun isMutable(): Boolean = true
    override fun disassemble(value: FloatArray?): Serializable? = value
    override fun assemble(cached: Serializable?, owner: Any?): FloatArray? = cached as? FloatArray
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :backend:core:test --tests "com.jobhunter.core.jpa.PgVectorTypeTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/core/src/main/kotlin/com/jobhunter/core/jpa/PgVectorType.kt \
        backend/core/src/test/kotlin/com/jobhunter/core/jpa/PgVectorTypeTest.kt
git commit -m "feat: add pgvector Hibernate UserType for FloatArray columns"
```

---

## Task 7: AbstractRepositoryTest base + Category enum mapping

**Files:**
- Create: `backend/core/src/test/kotlin/com/jobhunter/core/AbstractRepositoryTest.kt`
- Create: `backend/core/src/main/kotlin/com/jobhunter/core/domain/Category.kt`
- Create: `backend/core/src/main/kotlin/com/jobhunter/core/jpa/CategoryArrayType.kt`

The shared Testcontainers base avoids repeating Postgres-container boilerplate in every repo test. Category needs a custom Hibernate type because Postgres enum arrays don't map cleanly to JPA defaults.

- [ ] **Step 1: Create the abstract test base**

```kotlin
package com.jobhunter.core

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
abstract class AbstractRepositoryTest {
    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("pgvector/pgvector:pg16")
            .withDatabaseName("jobhunter")
            .withUsername("jobhunter")
            .withPassword("jobhunter")
            .withReuse(true)

        @DynamicPropertySource
        @JvmStatic
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.flyway.enabled") { "true" }
            registry.add("spring.jpa.hibernate.ddl-auto") { "validate" }
        }
    }
}
```

- [ ] **Step 2: Write a failing test that requires the Category enum**

Create `backend/core/src/test/kotlin/com/jobhunter/core/domain/CategoryTest.kt`:

```kotlin
package com.jobhunter.core.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CategoryTest {
    @Test
    fun `enum has all expected values`() {
        val names = Category.entries.map { it.name }.toSet()
        assertEquals(
            setOf(
                "SOFTWARE_BACKEND", "SOFTWARE_FULLSTACK", "SOFTWARE_FRONTEND",
                "DEVOPS", "SRE", "PLATFORM",
                "DATA_ENGINEERING", "DATA_SCIENCE", "EMBEDDED", "MOBILE",
                "QA_AUTOMATION", "SECURITY",
                "PRODUCT_MANAGEMENT", "DESIGN",
                "HUMAN_RESOURCES", "MARKETING", "SALES", "CUSTOMER_SUCCESS",
                "FINANCE", "LEGAL", "OPERATIONS", "ADMIN",
                "CONSTRUCTION", "HEALTHCARE", "EDUCATION", "OTHER",
            ),
            names,
        )
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

```bash
./gradlew :backend:core:test --tests "com.jobhunter.core.domain.CategoryTest"
```

Expected: FAIL — `Category` does not exist.

- [ ] **Step 4: Create the Category enum**

```kotlin
package com.jobhunter.core.domain

enum class Category {
    SOFTWARE_BACKEND, SOFTWARE_FULLSTACK, SOFTWARE_FRONTEND,
    DEVOPS, SRE, PLATFORM,
    DATA_ENGINEERING, DATA_SCIENCE, EMBEDDED, MOBILE,
    QA_AUTOMATION, SECURITY,
    PRODUCT_MANAGEMENT, DESIGN,
    HUMAN_RESOURCES, MARKETING, SALES, CUSTOMER_SUCCESS,
    FINANCE, LEGAL, OPERATIONS, ADMIN,
    CONSTRUCTION, HEALTHCARE, EDUCATION, OTHER,
}
```

- [ ] **Step 5: Create `CategoryArrayType` (Hibernate UserType for `category[]`)**

```kotlin
package com.jobhunter.core.jpa

import com.jobhunter.core.domain.Category
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.usertype.UserType
import java.io.Serializable
import java.sql.Array as SqlArray
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types

class CategoryArrayType : UserType<List<Category>> {
    override fun getSqlType(): Int = Types.ARRAY

    @Suppress("UNCHECKED_CAST")
    override fun returnedClass(): Class<List<Category>> = List::class.java as Class<List<Category>>

    override fun equals(x: List<Category>?, y: List<Category>?): Boolean = x == y
    override fun hashCode(x: List<Category>?): Int = x?.hashCode() ?: 0

    override fun nullSafeGet(
        rs: ResultSet,
        position: Int,
        session: SharedSessionContractImplementor?,
        owner: Any?,
    ): List<Category>? {
        val sqlArray = rs.getArray(position) ?: return null
        @Suppress("UNCHECKED_CAST")
        val raw = sqlArray.array as Array<String>
        return raw.map { Category.valueOf(it) }
    }

    override fun nullSafeSet(
        st: PreparedStatement,
        value: List<Category>?,
        index: Int,
        session: SharedSessionContractImplementor?,
    ) {
        if (value == null) {
            st.setNull(index, Types.ARRAY)
            return
        }
        val conn = st.connection
        val arr: SqlArray = conn.createArrayOf("category", value.map { it.name }.toTypedArray())
        st.setArray(index, arr)
    }

    override fun deepCopy(value: List<Category>?): List<Category>? = value?.toList()
    override fun isMutable(): Boolean = false
    override fun disassemble(value: List<Category>?): Serializable? = value?.let { ArrayList(it) }

    @Suppress("UNCHECKED_CAST")
    override fun assemble(cached: Serializable?, owner: Any?): List<Category>? =
        (cached as? ArrayList<Category>)?.toList()
}
```

- [ ] **Step 6: Run the test to verify it passes**

```bash
./gradlew :backend:core:test --tests "com.jobhunter.core.domain.CategoryTest"
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/core/src/test/kotlin/com/jobhunter/core/AbstractRepositoryTest.kt \
        backend/core/src/test/kotlin/com/jobhunter/core/domain/CategoryTest.kt \
        backend/core/src/main/kotlin/com/jobhunter/core/domain/Category.kt \
        backend/core/src/main/kotlin/com/jobhunter/core/jpa/CategoryArrayType.kt
git commit -m "feat: add Category enum, CategoryArrayType, and AbstractRepositoryTest"
```

---

## Task 8: JobSource entity + repository + test

**Files:**
- Create: `backend/core/src/main/kotlin/com/jobhunter/core/domain/JobSource.kt`
- Create: `backend/core/src/main/kotlin/com/jobhunter/core/domain/SourceType.kt`
- Create: `backend/core/src/main/kotlin/com/jobhunter/core/repository/JobSourceRepository.kt`
- Create: `backend/core/src/test/kotlin/com/jobhunter/core/repository/JobSourceRepositoryTest.kt`

- [ ] **Step 1: Write the failing repository test**

```kotlin
package com.jobhunter.core.repository

import com.jobhunter.core.AbstractRepositoryTest
import com.jobhunter.core.domain.JobSource
import com.jobhunter.core.domain.SourceType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class JobSourceRepositoryTest : AbstractRepositoryTest() {
    @Autowired lateinit var repository: JobSourceRepository

    @Test
    fun `saves and retrieves a job source with JSONB config`() {
        val source = JobSource(
            code = "IMAP_LINKEDIN",
            type = SourceType.IMAP,
            enabled = true,
            config = """{"folder":"INBOX","from":"jobs-noreply@linkedin.com"}""",
        )
        val saved = repository.save(source)
        assertNotNull(saved.id)

        val found = repository.findByCode("IMAP_LINKEDIN")
        assertNotNull(found)
        assertEquals(SourceType.IMAP, found.type)
        assertEquals(true, found.enabled)
    }

    @Test
    fun `code is unique`() {
        repository.save(JobSource("UNIQ", SourceType.SCRAPER, true, "{}"))
        val duplicate = JobSource("UNIQ", SourceType.SCRAPER, true, "{}")
        try {
            repository.saveAndFlush(duplicate)
            error("expected DataIntegrityViolationException")
        } catch (_: org.springframework.dao.DataIntegrityViolationException) {
            // expected
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :backend:core:test --tests "com.jobhunter.core.repository.JobSourceRepositoryTest"
```

Expected: FAIL — entity and repository do not exist.

- [ ] **Step 3: Create `SourceType` enum**

```kotlin
package com.jobhunter.core.domain

enum class SourceType { IMAP, SCRAPER }
```

- [ ] **Step 4: Create `JobSource` entity**

```kotlin
package com.jobhunter.core.domain

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "job_source")
class JobSource(
    @Column(name = "code", nullable = false, unique = true, length = 50)
    var code: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    var type: SourceType,

    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = true,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", nullable = false, columnDefinition = "jsonb")
    var config: String = "{}",

    @Column(name = "last_run_at")
    var lastRunAt: Instant? = null,

    @Column(name = "last_run_status", length = 20)
    var lastRunStatus: String? = null,

    @Column(name = "last_run_error")
    var lastRunError: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
```

- [ ] **Step 5: Create `JobSourceRepository`**

```kotlin
package com.jobhunter.core.repository

import com.jobhunter.core.domain.JobSource
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface JobSourceRepository : JpaRepository<JobSource, Long> {
    fun findByCode(code: String): JobSource?
    fun findByEnabledTrue(): List<JobSource>
}
```

- [ ] **Step 6: Run the test to verify it passes**

```bash
./gradlew :backend:core:test --tests "com.jobhunter.core.repository.JobSourceRepositoryTest"
```

Expected: PASS — both tests.

- [ ] **Step 7: Commit**

```bash
git add backend/core/src/main/kotlin/com/jobhunter/core/domain/JobSource.kt \
        backend/core/src/main/kotlin/com/jobhunter/core/domain/SourceType.kt \
        backend/core/src/main/kotlin/com/jobhunter/core/repository/JobSourceRepository.kt \
        backend/core/src/test/kotlin/com/jobhunter/core/repository/JobSourceRepositoryTest.kt
git commit -m "feat: add JobSource entity and repository"
```

---

## Task 9: JobPosting entity + repository + test

**Files:**
- Create: `backend/core/src/main/kotlin/com/jobhunter/core/domain/JobPosting.kt`
- Create: `backend/core/src/main/kotlin/com/jobhunter/core/repository/JobPostingRepository.kt`
- Create: `backend/core/src/test/kotlin/com/jobhunter/core/repository/JobPostingRepositoryTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.jobhunter.core.repository

import com.jobhunter.core.AbstractRepositoryTest
import com.jobhunter.core.domain.Category
import com.jobhunter.core.domain.JobPosting
import com.jobhunter.core.domain.JobSource
import com.jobhunter.core.domain.SourceType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JobPostingRepositoryTest : AbstractRepositoryTest() {
    @Autowired lateinit var sources: JobSourceRepository
    @Autowired lateinit var postings: JobPostingRepository

    @Test
    fun `saves and retrieves a posting with categories`() {
        val source = sources.save(JobSource("S1", SourceType.IMAP, true, "{}"))
        val posting = JobPosting(
            sourceId = source.id!!,
            externalId = "EXT-1",
            rawText = "We need a Kotlin engineer.",
            title = "Backend Engineer",
            company = "Acme",
            language = "en",
            contactEmail = "jobs@acme.com",
            categories = listOf(Category.SOFTWARE_BACKEND),
            capturedAt = Instant.now(),
        )
        val saved = postings.save(posting)
        assertNotNull(saved.id)

        val found = postings.findById(saved.id!!).get()
        assertEquals("EXT-1", found.externalId)
        assertEquals(listOf(Category.SOFTWARE_BACKEND), found.categories)
        assertEquals("jobs@acme.com", found.contactEmail)
    }

    @Test
    fun `unique source plus external id is enforced`() {
        val source = sources.save(JobSource("S2", SourceType.SCRAPER, true, "{}"))
        postings.save(JobPosting(
            sourceId = source.id!!, externalId = "DUP", rawText = "x", capturedAt = Instant.now(),
        ))
        try {
            postings.saveAndFlush(JobPosting(
                sourceId = source.id!!, externalId = "DUP", rawText = "y", capturedAt = Instant.now(),
            ))
            error("expected DataIntegrityViolationException")
        } catch (_: org.springframework.dao.DataIntegrityViolationException) {
            // expected
        }
    }

    @Test
    fun `findBySourceIdAndExternalId returns existing or null`() {
        val source = sources.save(JobSource("S3", SourceType.IMAP, true, "{}"))
        postings.save(JobPosting(
            sourceId = source.id!!, externalId = "FIND-ME", rawText = "x", capturedAt = Instant.now(),
        ))
        assertNotNull(postings.findBySourceIdAndExternalId(source.id!!, "FIND-ME"))
        assertTrue(postings.findBySourceIdAndExternalId(source.id!!, "MISSING") == null)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :backend:core:test --tests "com.jobhunter.core.repository.JobPostingRepositoryTest"
```

Expected: FAIL — entity and repository do not exist.

- [ ] **Step 3: Create `JobPosting` entity**

```kotlin
package com.jobhunter.core.domain

import com.jobhunter.core.jpa.CategoryArrayType
import jakarta.persistence.*
import org.hibernate.annotations.Type
import java.time.Instant

@Entity
@Table(
    name = "job_posting",
    uniqueConstraints = [UniqueConstraint(columnNames = ["source_id", "external_id"])],
)
class JobPosting(
    @Column(name = "source_id", nullable = false)
    var sourceId: Long,

    @Column(name = "external_id", nullable = false, length = 255)
    var externalId: String,

    @Column(name = "raw_text", nullable = false, columnDefinition = "text")
    var rawText: String,

    @Column(name = "raw_html", columnDefinition = "text")
    var rawHtml: String? = null,

    @Column(name = "source_url", columnDefinition = "text")
    var sourceUrl: String? = null,

    @Column(name = "title", length = 500)
    var title: String? = null,

    @Column(name = "company", length = 255)
    var company: String? = null,

    @Column(name = "location", length = 255)
    var location: String? = null,

    @Column(name = "is_remote")
    var isRemote: Boolean? = null,

    @Column(name = "language", columnDefinition = "char(2)")
    var language: String? = null,

    @Column(name = "contact_email", length = 255)
    var contactEmail: String? = null,

    @Column(name = "apply_url", columnDefinition = "text")
    var applyUrl: String? = null,

    @Column(name = "description", columnDefinition = "text")
    var description: String? = null,

    @Column(name = "requirements", columnDefinition = "text")
    var requirements: String? = null,

    @Column(name = "salary_text", length = 255)
    var salaryText: String? = null,

    @Type(CategoryArrayType::class)
    @Column(name = "categories", columnDefinition = "category[]")
    var categories: List<Category>? = null,

    @Column(name = "posted_at")
    var postedAt: Instant? = null,

    @Column(name = "captured_at", nullable = false)
    var capturedAt: Instant = Instant.now(),

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
```

- [ ] **Step 4: Create `JobPostingRepository`**

```kotlin
package com.jobhunter.core.repository

import com.jobhunter.core.domain.JobPosting
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface JobPostingRepository : JpaRepository<JobPosting, Long> {
    fun findBySourceIdAndExternalId(sourceId: Long, externalId: String): JobPosting?
}
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
./gradlew :backend:core:test --tests "com.jobhunter.core.repository.JobPostingRepositoryTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/core/src/main/kotlin/com/jobhunter/core/domain/JobPosting.kt \
        backend/core/src/main/kotlin/com/jobhunter/core/repository/JobPostingRepository.kt \
        backend/core/src/test/kotlin/com/jobhunter/core/repository/JobPostingRepositoryTest.kt
git commit -m "feat: add JobPosting entity and repository"
```

---

## Task 10: PostingEmbedding entity + repository + test (pgvector roundtrip)

**Files:**
- Create: `backend/core/src/main/kotlin/com/jobhunter/core/domain/PostingEmbedding.kt`
- Create: `backend/core/src/main/kotlin/com/jobhunter/core/repository/PostingEmbeddingRepository.kt`
- Create: `backend/core/src/test/kotlin/com/jobhunter/core/repository/PostingEmbeddingRepositoryTest.kt`

This is the first integration test that exercises the pgvector custom type end-to-end.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.jobhunter.core.repository

import com.jobhunter.core.AbstractRepositoryTest
import com.jobhunter.core.domain.JobPosting
import com.jobhunter.core.domain.JobSource
import com.jobhunter.core.domain.PostingEmbedding
import com.jobhunter.core.domain.SourceType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import kotlin.test.assertEquals

class PostingEmbeddingRepositoryTest : AbstractRepositoryTest() {
    @Autowired lateinit var sources: JobSourceRepository
    @Autowired lateinit var postings: JobPostingRepository
    @Autowired lateinit var embeddings: PostingEmbeddingRepository

    @Test
    fun `roundtrips a 1024-dim vector`() {
        val source = sources.save(JobSource("S", SourceType.IMAP, true, "{}"))
        val posting = postings.save(JobPosting(
            sourceId = source.id!!, externalId = "E", rawText = "x", capturedAt = Instant.now(),
        ))
        val vec = FloatArray(1024) { i -> i / 1024f }
        val saved = embeddings.save(PostingEmbedding(
            jobPostingId = posting.id!!,
            embedding = vec,
            modelName = "bge-m3",
        ))
        val found = embeddings.findById(saved.jobPostingId).get()
        assertEquals(vec.toList(), found.embedding.toList())
        assertEquals("bge-m3", found.modelName)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :backend:core:test --tests "com.jobhunter.core.repository.PostingEmbeddingRepositoryTest"
```

Expected: FAIL — entity and repository do not exist.

- [ ] **Step 3: Create `PostingEmbedding` entity**

```kotlin
package com.jobhunter.core.domain

import com.jobhunter.core.jpa.PgVectorType
import jakarta.persistence.*
import org.hibernate.annotations.Type
import java.time.Instant

@Entity
@Table(name = "posting_embedding")
class PostingEmbedding(
    @Id
    @Column(name = "job_posting_id")
    var jobPostingId: Long,

    @Type(PgVectorType::class)
    @Column(name = "embedding", nullable = false, columnDefinition = "vector(1024)")
    var embedding: FloatArray,

    @Column(name = "model_name", nullable = false, length = 100)
    var modelName: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),
)
```

- [ ] **Step 4: Create `PostingEmbeddingRepository`**

```kotlin
package com.jobhunter.core.repository

import com.jobhunter.core.domain.PostingEmbedding
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PostingEmbeddingRepository : JpaRepository<PostingEmbedding, Long>
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
./gradlew :backend:core:test --tests "com.jobhunter.core.repository.PostingEmbeddingRepositoryTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/core/src/main/kotlin/com/jobhunter/core/domain/PostingEmbedding.kt \
        backend/core/src/main/kotlin/com/jobhunter/core/repository/PostingEmbeddingRepository.kt \
        backend/core/src/test/kotlin/com/jobhunter/core/repository/PostingEmbeddingRepositoryTest.kt
git commit -m "feat: add PostingEmbedding entity with pgvector roundtrip test"
```

---

## Task 11: ProcessingQueue entity + state + repository + test

**Files:**
- Create: `backend/core/src/main/kotlin/com/jobhunter/core/domain/QueueState.kt`
- Create: `backend/core/src/main/kotlin/com/jobhunter/core/domain/ProcessingQueueRow.kt`
- Create: `backend/core/src/main/kotlin/com/jobhunter/core/repository/ProcessingQueueRepository.kt`
- Create: `backend/core/src/test/kotlin/com/jobhunter/core/repository/ProcessingQueueRepositoryTest.kt`

The repository test verifies `FOR UPDATE SKIP LOCKED` work-claiming semantics.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.jobhunter.core.repository

import com.jobhunter.core.AbstractRepositoryTest
import com.jobhunter.core.domain.JobPosting
import com.jobhunter.core.domain.JobSource
import com.jobhunter.core.domain.ProcessingQueueRow
import com.jobhunter.core.domain.QueueState
import com.jobhunter.core.domain.SourceType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import kotlin.test.assertEquals

class ProcessingQueueRepositoryTest : AbstractRepositoryTest() {
    @Autowired lateinit var sources: JobSourceRepository
    @Autowired lateinit var postings: JobPostingRepository
    @Autowired lateinit var queue: ProcessingQueueRepository

    @Test
    fun `claims oldest row in target state`() {
        val source = sources.save(JobSource("S", SourceType.IMAP, true, "{}"))
        repeat(3) { i ->
            val p = postings.save(JobPosting(
                sourceId = source.id!!, externalId = "E$i", rawText = "x", capturedAt = Instant.now(),
            ))
            queue.save(ProcessingQueueRow(jobPostingId = p.id!!, state = QueueState.INGESTED))
        }
        val claimed = queue.claimNext(QueueState.INGESTED.name, 2)
        assertEquals(2, claimed.size)
    }

    @Test
    fun `respects next_attempt_at backoff`() {
        val source = sources.save(JobSource("S2", SourceType.IMAP, true, "{}"))
        val p = postings.save(JobPosting(
            sourceId = source.id!!, externalId = "X", rawText = "x", capturedAt = Instant.now(),
        ))
        queue.save(ProcessingQueueRow(
            jobPostingId = p.id!!,
            state = QueueState.INGESTED,
            nextAttemptAt = Instant.now().plusSeconds(60),
        ))
        val claimed = queue.claimNext(QueueState.INGESTED.name, 5)
        assertEquals(0, claimed.size)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :backend:core:test --tests "com.jobhunter.core.repository.ProcessingQueueRepositoryTest"
```

Expected: FAIL — types do not exist.

- [ ] **Step 3: Create `QueueState` enum**

```kotlin
package com.jobhunter.core.domain

enum class QueueState {
    INGESTED, PARSED, CLASSIFIED, EMBEDDED, MATCHED,
    IRRELEVANT, OUT_OF_SCOPE, FAILED,
}
```

- [ ] **Step 4: Create `ProcessingQueueRow` entity**

```kotlin
package com.jobhunter.core.domain

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "processing_queue")
class ProcessingQueueRow(
    @Column(name = "job_posting_id", nullable = false)
    var jobPostingId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 30)
    var state: QueueState,

    @Column(name = "attempts", nullable = false)
    var attempts: Int = 0,

    @Column(name = "last_error", columnDefinition = "text")
    var lastError: String? = null,

    @Column(name = "next_attempt_at")
    var nextAttemptAt: Instant? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
```

- [ ] **Step 5: Create `ProcessingQueueRepository` with native claim query**

```kotlin
package com.jobhunter.core.repository

import com.jobhunter.core.domain.ProcessingQueueRow
import com.jobhunter.core.domain.QueueState
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ProcessingQueueRepository : JpaRepository<ProcessingQueueRow, Long> {

    @Query(
        value = """
            SELECT * FROM processing_queue
            WHERE state = :state
              AND (next_attempt_at IS NULL OR next_attempt_at <= now())
            ORDER BY id
            LIMIT :batch
            FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true,
    )
    fun claimNext(@Param("state") state: String, @Param("batch") batch: Int): List<ProcessingQueueRow>

    fun findByState(state: QueueState): List<ProcessingQueueRow>
    fun countByState(state: QueueState): Long
}
```

- [ ] **Step 6: Run the test to verify it passes**

```bash
./gradlew :backend:core:test --tests "com.jobhunter.core.repository.ProcessingQueueRepositoryTest"
```

Expected: PASS — both tests.

- [ ] **Step 7: Commit**

```bash
git add backend/core/src/main/kotlin/com/jobhunter/core/domain/QueueState.kt \
        backend/core/src/main/kotlin/com/jobhunter/core/domain/ProcessingQueueRow.kt \
        backend/core/src/main/kotlin/com/jobhunter/core/repository/ProcessingQueueRepository.kt \
        backend/core/src/test/kotlin/com/jobhunter/core/repository/ProcessingQueueRepositoryTest.kt
git commit -m "feat: add ProcessingQueue entity, state, and repository with claim query"
```

---

## Task 12: Cv entity + repository + test

**Files:**
- Create: `backend/core/src/main/kotlin/com/jobhunter/core/domain/Cv.kt`
- Create: `backend/core/src/main/kotlin/com/jobhunter/core/repository/CvRepository.kt`
- Create: `backend/core/src/test/kotlin/com/jobhunter/core/repository/CvRepositoryTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.jobhunter.core.repository

import com.jobhunter.core.AbstractRepositoryTest
import com.jobhunter.core.domain.Cv
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CvRepositoryTest : AbstractRepositoryTest() {
    @Autowired lateinit var repository: CvRepository

    @Test
    fun `saves and retrieves a CV with bytes and embedding`() {
        val saved = repository.save(Cv(
            label = "default",
            fileName = "shlomi.pdf",
            mimeType = "application/pdf",
            fileBytes = byteArrayOf(1, 2, 3, 4),
            parsedText = "Kotlin engineer with 7 years experience.",
            embedding = FloatArray(1024) { 0.1f },
            structuredSummary = """{"skills":["kotlin"],"years":7}""",
            isActive = true,
        ))
        assertNotNull(saved.id)
        val found = repository.findById(saved.id!!).get()
        assertEquals("default", found.label)
        assertEquals(4, found.fileBytes.size)
        assertEquals(1024, found.embedding.size)
        assertEquals(true, found.isActive)
    }

    @Test
    fun `findActive returns the single active CV`() {
        repository.save(Cv("a", "a.pdf", "application/pdf", byteArrayOf(0), "x", FloatArray(1024), null, false))
        val active = repository.save(Cv("b", "b.pdf", "application/pdf", byteArrayOf(0), "x", FloatArray(1024), null, true))
        assertEquals(active.id, repository.findActive()?.id)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :backend:core:test --tests "com.jobhunter.core.repository.CvRepositoryTest"
```

Expected: FAIL — entity and repository do not exist.

- [ ] **Step 3: Create `Cv` entity**

```kotlin
package com.jobhunter.core.domain

import com.jobhunter.core.jpa.PgVectorType
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.Type
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "cv")
class Cv(
    @Column(name = "label", nullable = false, length = 100)
    var label: String,

    @Column(name = "file_name", nullable = false, length = 255)
    var fileName: String,

    @Column(name = "mime_type", nullable = false, length = 100)
    var mimeType: String,

    @Column(name = "file_bytes", nullable = false)
    var fileBytes: ByteArray,

    @Column(name = "parsed_text", nullable = false, columnDefinition = "text")
    var parsedText: String,

    @Type(PgVectorType::class)
    @Column(name = "embedding", nullable = false, columnDefinition = "vector(1024)")
    var embedding: FloatArray,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "structured_summary", columnDefinition = "jsonb")
    var structuredSummary: String? = null,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = false,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
```

- [ ] **Step 4: Create `CvRepository`**

```kotlin
package com.jobhunter.core.repository

import com.jobhunter.core.domain.Cv
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface CvRepository : JpaRepository<Cv, Long> {
    @Query("SELECT c FROM Cv c WHERE c.isActive = true")
    fun findActive(): Cv?
}
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
./gradlew :backend:core:test --tests "com.jobhunter.core.repository.CvRepositoryTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/core/src/main/kotlin/com/jobhunter/core/domain/Cv.kt \
        backend/core/src/main/kotlin/com/jobhunter/core/repository/CvRepository.kt \
        backend/core/src/test/kotlin/com/jobhunter/core/repository/CvRepositoryTest.kt
git commit -m "feat: add Cv entity and repository"
```

---

## Task 13: Match entity + state + repository + test

**Files:**
- Create: `backend/core/src/main/kotlin/com/jobhunter/core/domain/MatchState.kt`
- Create: `backend/core/src/main/kotlin/com/jobhunter/core/domain/Match.kt`
- Create: `backend/core/src/main/kotlin/com/jobhunter/core/repository/MatchRepository.kt`
- Create: `backend/core/src/test/kotlin/com/jobhunter/core/repository/MatchRepositoryTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.jobhunter.core.repository

import com.jobhunter.core.AbstractRepositoryTest
import com.jobhunter.core.domain.Cv
import com.jobhunter.core.domain.JobPosting
import com.jobhunter.core.domain.JobSource
import com.jobhunter.core.domain.Match
import com.jobhunter.core.domain.MatchState
import com.jobhunter.core.domain.SourceType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import kotlin.test.assertEquals

class MatchRepositoryTest : AbstractRepositoryTest() {
    @Autowired lateinit var sources: JobSourceRepository
    @Autowired lateinit var postings: JobPostingRepository
    @Autowired lateinit var cvs: CvRepository
    @Autowired lateinit var matches: MatchRepository

    @Test
    fun `saves and retrieves a match with reasoning JSON`() {
        val source = sources.save(JobSource("S", SourceType.IMAP, true, "{}"))
        val posting = postings.save(JobPosting(
            sourceId = source.id!!, externalId = "E", rawText = "x", capturedAt = Instant.now(),
        ))
        val cv = cvs.save(Cv("d", "d.pdf", "application/pdf", byteArrayOf(0), "x", FloatArray(1024), null, true))

        val saved = matches.save(Match(
            jobPostingId = posting.id!!,
            cvId = cv.id!!,
            cosineSimilarity = 0.78,
            llmScore = 82,
            llmReasoning = """{"strengths":["kotlin"],"gaps":[]}""",
            state = MatchState.READY_FOR_REVIEW,
        ))
        val found = matches.findById(saved.id!!).get()
        assertEquals(82, found.llmScore)
        assertEquals(MatchState.READY_FOR_REVIEW, found.state)
    }

    @Test
    fun `findReadyForReview orders by score desc`() {
        val source = sources.save(JobSource("S2", SourceType.SCRAPER, true, "{}"))
        val cv = cvs.save(Cv("d2", "d.pdf", "application/pdf", byteArrayOf(0), "x", FloatArray(1024), null, true))
        listOf(50, 90, 70).forEachIndexed { i, score ->
            val p = postings.save(JobPosting(
                sourceId = source.id!!, externalId = "E$i", rawText = "x", capturedAt = Instant.now(),
            ))
            matches.save(Match(
                jobPostingId = p.id!!, cvId = cv.id!!, cosineSimilarity = 0.5, llmScore = score,
                state = MatchState.READY_FOR_REVIEW,
            ))
        }
        val ordered = matches.findReadyForReview()
        assertEquals(listOf(90, 70, 50), ordered.map { it.llmScore })
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :backend:core:test --tests "com.jobhunter.core.repository.MatchRepositoryTest"
```

Expected: FAIL.

- [ ] **Step 3: Create `MatchState` enum**

```kotlin
package com.jobhunter.core.domain

enum class MatchState {
    READY_FOR_REVIEW, DRAFTED, SENT, SKIPPED, SEND_FAILED,
}
```

- [ ] **Step 4: Create `Match` entity**

```kotlin
package com.jobhunter.core.domain

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(
    name = "match",
    uniqueConstraints = [UniqueConstraint(columnNames = ["job_posting_id", "cv_id"])],
)
class Match(
    @Column(name = "job_posting_id", nullable = false)
    var jobPostingId: Long,

    @Column(name = "cv_id", nullable = false)
    var cvId: Long,

    @Column(name = "cosine_similarity", nullable = false)
    var cosineSimilarity: Double,

    @Column(name = "llm_score")
    var llmScore: Int? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "llm_reasoning", columnDefinition = "jsonb")
    var llmReasoning: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 30)
    var state: MatchState,

    @Column(name = "draft_subject", length = 500)
    var draftSubject: String? = null,

    @Column(name = "draft_body", columnDefinition = "text")
    var draftBody: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
```

- [ ] **Step 5: Create `MatchRepository`**

```kotlin
package com.jobhunter.core.repository

import com.jobhunter.core.domain.Match
import com.jobhunter.core.domain.MatchState
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface MatchRepository : JpaRepository<Match, Long> {
    fun findByState(state: MatchState): List<Match>

    @Query("""
        SELECT m FROM Match m
        WHERE m.state = com.jobhunter.core.domain.MatchState.READY_FOR_REVIEW
        ORDER BY m.llmScore DESC NULLS LAST, m.id ASC
    """)
    fun findReadyForReview(): List<Match>
}
```

- [ ] **Step 6: Run the test to verify it passes**

```bash
./gradlew :backend:core:test --tests "com.jobhunter.core.repository.MatchRepositoryTest"
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/core/src/main/kotlin/com/jobhunter/core/domain/MatchState.kt \
        backend/core/src/main/kotlin/com/jobhunter/core/domain/Match.kt \
        backend/core/src/main/kotlin/com/jobhunter/core/repository/MatchRepository.kt \
        backend/core/src/test/kotlin/com/jobhunter/core/repository/MatchRepositoryTest.kt
git commit -m "feat: add Match entity, state, and repository"
```

---

## Task 14: EmailSendRecord entity + repository + test

**Files:**
- Create: `backend/core/src/main/kotlin/com/jobhunter/core/domain/EmailSendRecord.kt`
- Create: `backend/core/src/main/kotlin/com/jobhunter/core/repository/EmailSendRecordRepository.kt`
- Create: `backend/core/src/test/kotlin/com/jobhunter/core/repository/EmailSendRecordRepositoryTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.jobhunter.core.repository

import com.jobhunter.core.AbstractRepositoryTest
import com.jobhunter.core.domain.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import kotlin.test.assertEquals

class EmailSendRecordRepositoryTest : AbstractRepositoryTest() {
    @Autowired lateinit var sources: JobSourceRepository
    @Autowired lateinit var postings: JobPostingRepository
    @Autowired lateinit var cvs: CvRepository
    @Autowired lateinit var matches: MatchRepository
    @Autowired lateinit var sends: EmailSendRecordRepository

    @Test
    fun `saves and retrieves a send record`() {
        val src = sources.save(JobSource("S", SourceType.IMAP, true, "{}"))
        val post = postings.save(JobPosting(
            sourceId = src.id!!, externalId = "E", rawText = "x", capturedAt = Instant.now(),
        ))
        val cv = cvs.save(Cv("d", "d.pdf", "application/pdf", byteArrayOf(0), "x", FloatArray(1024), null, true))
        val match = matches.save(Match(
            jobPostingId = post.id!!, cvId = cv.id!!,
            cosineSimilarity = 0.8, llmScore = 85, state = MatchState.SENT,
        ))
        val saved = sends.save(EmailSendRecord(
            matchId = match.id!!,
            cvId = cv.id!!,
            toAddress = "jobs@acme.com",
            subject = "Application",
            body = "Hi",
            attachmentFilename = "shlomi.pdf",
            sentAt = Instant.now(),
            smtpMessageId = "<abc@gmail.com>",
            status = "SENT",
        ))
        val found = sends.findById(saved.id!!).get()
        assertEquals("jobs@acme.com", found.toAddress)
        assertEquals("SENT", found.status)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :backend:core:test --tests "com.jobhunter.core.repository.EmailSendRecordRepositoryTest"
```

Expected: FAIL.

- [ ] **Step 3: Create `EmailSendRecord` entity**

```kotlin
package com.jobhunter.core.domain

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "email_send_record")
class EmailSendRecord(
    @Column(name = "match_id", nullable = false, unique = true)
    var matchId: Long,

    @Column(name = "cv_id", nullable = false)
    var cvId: Long,

    @Column(name = "to_address", nullable = false, length = 255)
    var toAddress: String,

    @Column(name = "subject", nullable = false, length = 500)
    var subject: String,

    @Column(name = "body", nullable = false, columnDefinition = "text")
    var body: String,

    @Column(name = "attachment_filename", length = 255)
    var attachmentFilename: String? = null,

    @Column(name = "sent_at", nullable = false)
    var sentAt: Instant,

    @Column(name = "smtp_message_id", length = 255)
    var smtpMessageId: String? = null,

    @Column(name = "status", nullable = false, length = 20)
    var status: String,

    @Column(name = "failure_reason", columnDefinition = "text")
    var failureReason: String? = null,

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
```

- [ ] **Step 4: Create `EmailSendRecordRepository`**

```kotlin
package com.jobhunter.core.repository

import com.jobhunter.core.domain.EmailSendRecord
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface EmailSendRecordRepository : JpaRepository<EmailSendRecord, Long> {
    fun findByMatchId(matchId: Long): EmailSendRecord?
}
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
./gradlew :backend:core:test --tests "com.jobhunter.core.repository.EmailSendRecordRepositoryTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/core/src/main/kotlin/com/jobhunter/core/domain/EmailSendRecord.kt \
        backend/core/src/main/kotlin/com/jobhunter/core/repository/EmailSendRecordRepository.kt \
        backend/core/src/test/kotlin/com/jobhunter/core/repository/EmailSendRecordRepositoryTest.kt
git commit -m "feat: add EmailSendRecord entity and repository"
```

---

## Task 15: LlmClient interface + Ollama implementation + test

**Files:**
- Create: `backend/core/src/main/kotlin/com/jobhunter/core/client/LlmClient.kt`
- Create: `backend/core/src/main/kotlin/com/jobhunter/core/client/OllamaLlmClient.kt`
- Create: `backend/core/src/test/kotlin/com/jobhunter/core/client/OllamaLlmClientTest.kt`

The wrapper provides a single typed entry point for all LLM use-sites in later modules. Structured output is a JSON string the caller deserializes with Jackson.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.jobhunter.core.client

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.prompt.Prompt
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OllamaLlmClientTest {

    private fun chatModelReturning(text: String): ChatModel {
        val model = mockk<ChatModel>()
        val response = ChatResponse(listOf(Generation(AssistantMessage(text))))
        every { model.call(any<Prompt>()) } returns response
        return model
    }

    @Test
    fun `chat returns assistant text`() {
        val client = OllamaLlmClient(chatModelReturning("hello"))
        assertEquals("hello", client.chat(system = "be brief", user = "hi"))
    }

    @Test
    fun `chatStructured strips markdown fences and returns JSON`() {
        val client = OllamaLlmClient(chatModelReturning("```json\n{\"x\":1}\n```"))
        val json = client.chatStructured(system = "json only", user = "go")
        assertEquals("{\"x\":1}", json)
    }

    @Test
    fun `chat passes system and user messages to model`() {
        val model = chatModelReturning("ok")
        val client = OllamaLlmClient(model)
        client.chat("S", "U")
        verify {
            model.call(match<Prompt> { prompt ->
                val msgs = prompt.instructions
                msgs.size == 2 && msgs[0].text == "S" && msgs[1].text == "U"
            })
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :backend:core:test --tests "com.jobhunter.core.client.OllamaLlmClientTest"
```

Expected: FAIL — types do not exist.

- [ ] **Step 3: Create `LlmClient` interface**

```kotlin
package com.jobhunter.core.client

interface LlmClient {
    fun chat(system: String, user: String): String

    /**
     * Like [chat], but expects the response to be a JSON object/array.
     * Strips common markdown fences before returning.
     * The caller deserializes with Jackson against their own schema.
     */
    fun chatStructured(system: String, user: String): String
}
```

- [ ] **Step 4: Implement `OllamaLlmClient`**

```kotlin
package com.jobhunter.core.client

import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.stereotype.Component
import java.util.concurrent.Semaphore

@Component
class OllamaLlmClient(
    private val chatModel: ChatModel,
) : LlmClient {

    /**
     * The 32B model serializes naturally on a single Mac; we cap concurrency
     * at 1 to avoid OOM and contention.
     */
    private val semaphore = Semaphore(1)

    override fun chat(system: String, user: String): String {
        semaphore.acquire()
        try {
            val prompt = Prompt(listOf(SystemMessage(system), UserMessage(user)))
            return chatModel.call(prompt).result.output.text ?: ""
        } finally {
            semaphore.release()
        }
    }

    override fun chatStructured(system: String, user: String): String =
        stripFences(chat(system, user))

    private fun stripFences(raw: String): String {
        val trimmed = raw.trim()
        val fenced = Regex("^```(?:json)?\\s*(.*?)\\s*```$", RegexOption.DOT_MATCHES_ALL)
        return fenced.matchEntire(trimmed)?.groupValues?.get(1)?.trim() ?: trimmed
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
./gradlew :backend:core:test --tests "com.jobhunter.core.client.OllamaLlmClientTest"
```

Expected: PASS — all three tests.

- [ ] **Step 6: Commit**

```bash
git add backend/core/src/main/kotlin/com/jobhunter/core/client/LlmClient.kt \
        backend/core/src/main/kotlin/com/jobhunter/core/client/OllamaLlmClient.kt \
        backend/core/src/test/kotlin/com/jobhunter/core/client/OllamaLlmClientTest.kt
git commit -m "feat: add LlmClient interface and Ollama implementation"
```

---

## Task 16: EmbeddingClient interface + Ollama implementation + test

**Files:**
- Create: `backend/core/src/main/kotlin/com/jobhunter/core/client/EmbeddingClient.kt`
- Create: `backend/core/src/main/kotlin/com/jobhunter/core/client/OllamaEmbeddingClient.kt`
- Create: `backend/core/src/test/kotlin/com/jobhunter/core/client/OllamaEmbeddingClientTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.jobhunter.core.client

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.embedding.EmbeddingRequest
import org.springframework.ai.embedding.EmbeddingResponse
import org.springframework.ai.embedding.Embedding
import kotlin.test.assertEquals

class OllamaEmbeddingClientTest {

    @Test
    fun `embed returns vector from underlying model`() {
        val model = mockk<EmbeddingModel>()
        val expected = floatArrayOf(0.1f, 0.2f, 0.3f)
        every { model.call(any<EmbeddingRequest>()) } returns EmbeddingResponse(
            listOf(Embedding(expected, 0)),
        )

        val client = OllamaEmbeddingClient(model)
        val result = client.embed("hello")
        assertEquals(expected.toList(), result.toList())
    }

    @Test
    fun `embed throws when model returns empty`() {
        val model = mockk<EmbeddingModel>()
        every { model.call(any<EmbeddingRequest>()) } returns EmbeddingResponse(emptyList())
        val client = OllamaEmbeddingClient(model)
        try {
            client.embed("hi")
            error("expected IllegalStateException")
        } catch (_: IllegalStateException) {
            // expected
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :backend:core:test --tests "com.jobhunter.core.client.OllamaEmbeddingClientTest"
```

Expected: FAIL.

- [ ] **Step 3: Create `EmbeddingClient` interface**

```kotlin
package com.jobhunter.core.client

interface EmbeddingClient {
    fun embed(text: String): FloatArray
}
```

- [ ] **Step 4: Implement `OllamaEmbeddingClient`**

```kotlin
package com.jobhunter.core.client

import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.embedding.EmbeddingRequest
import org.springframework.stereotype.Component

@Component
class OllamaEmbeddingClient(
    private val embeddingModel: EmbeddingModel,
) : EmbeddingClient {
    override fun embed(text: String): FloatArray {
        val response = embeddingModel.call(EmbeddingRequest(listOf(text), null))
        val first = response.results.firstOrNull()
            ?: error("Embedding model returned no results")
        return first.output
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
./gradlew :backend:core:test --tests "com.jobhunter.core.client.OllamaEmbeddingClientTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/core/src/main/kotlin/com/jobhunter/core/client/EmbeddingClient.kt \
        backend/core/src/main/kotlin/com/jobhunter/core/client/OllamaEmbeddingClient.kt \
        backend/core/src/test/kotlin/com/jobhunter/core/client/OllamaEmbeddingClientTest.kt
git commit -m "feat: add EmbeddingClient interface and Ollama implementation"
```

---

## Task 17: QueueWorker abstract base + test

**Files:**
- Create: `backend/core/src/main/kotlin/com/jobhunter/core/worker/QueueWorker.kt`
- Create: `backend/core/src/test/kotlin/com/jobhunter/core/worker/QueueWorkerTest.kt`

The base class encapsulates: claim → process → advance state OR back-off-and-retry, with attempt tracking. Concrete workers in later plans implement only `inputState`, `outputState`, and `process()`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.jobhunter.core.worker

import com.jobhunter.core.AbstractRepositoryTest
import com.jobhunter.core.domain.JobPosting
import com.jobhunter.core.domain.JobSource
import com.jobhunter.core.domain.ProcessingQueueRow
import com.jobhunter.core.domain.QueueState
import com.jobhunter.core.domain.SourceType
import com.jobhunter.core.repository.JobPostingRepository
import com.jobhunter.core.repository.JobSourceRepository
import com.jobhunter.core.repository.ProcessingQueueRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class QueueWorkerTest : AbstractRepositoryTest() {
    @Autowired lateinit var sources: JobSourceRepository
    @Autowired lateinit var postings: JobPostingRepository
    @Autowired lateinit var queue: ProcessingQueueRepository
    @Autowired lateinit var txManager: PlatformTransactionManager

    private fun seedRow(state: QueueState): ProcessingQueueRow {
        val src = sources.save(JobSource("S-${System.nanoTime()}", SourceType.IMAP, true, "{}"))
        val p = postings.save(JobPosting(
            sourceId = src.id!!, externalId = "E-${System.nanoTime()}",
            rawText = "x", capturedAt = Instant.now(),
        ))
        return queue.save(ProcessingQueueRow(jobPostingId = p.id!!, state = state))
    }

    @Test
    fun `successful processing advances state`() {
        val row = seedRow(QueueState.INGESTED)
        val worker = TestWorker(
            input = QueueState.INGESTED,
            output = QueueState.PARSED,
            queue = queue,
            txManager = txManager,
            maxAttempts = 3,
        ) { /* succeed */ }

        worker.runOnce()

        val updated = queue.findById(row.id!!).get()
        assertEquals(QueueState.PARSED, updated.state)
        assertEquals(0, updated.attempts) // reset on advance
    }

    @Test
    fun `failure increments attempts and sets backoff`() {
        val row = seedRow(QueueState.INGESTED)
        val worker = TestWorker(
            input = QueueState.INGESTED,
            output = QueueState.PARSED,
            queue = queue,
            txManager = txManager,
            maxAttempts = 3,
        ) { throw RuntimeException("boom") }

        worker.runOnce()

        val updated = queue.findById(row.id!!).get()
        assertEquals(QueueState.INGESTED, updated.state)
        assertEquals(1, updated.attempts)
        assertEquals("boom", updated.lastError)
        assertNotNull(updated.nextAttemptAt)
    }

    @Test
    fun `exhausting attempts moves to FAILED`() {
        val row = seedRow(QueueState.INGESTED)
        val worker = TestWorker(
            input = QueueState.INGESTED,
            output = QueueState.PARSED,
            queue = queue,
            txManager = txManager,
            maxAttempts = 2,
        ) { throw RuntimeException("nope") }

        worker.runOnce()  // attempt 1
        // bypass backoff for the test
        queue.findById(row.id!!).get().also {
            it.nextAttemptAt = null
            queue.save(it)
        }
        worker.runOnce()  // attempt 2 — should now fail terminally

        val updated = queue.findById(row.id!!).get()
        assertEquals(QueueState.FAILED, updated.state)
        assertEquals(2, updated.attempts)
    }

    private class TestWorker(
        input: QueueState,
        output: QueueState,
        queue: ProcessingQueueRepository,
        txManager: PlatformTransactionManager,
        maxAttempts: Int,
        private val body: (Long) -> Unit,
    ) : QueueWorker(input, output, queue, txManager, maxAttempts) {
        override fun process(jobPostingId: Long) = body(jobPostingId)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :backend:core:test --tests "com.jobhunter.core.worker.QueueWorkerTest"
```

Expected: FAIL — `QueueWorker` does not exist.

- [ ] **Step 3: Implement `QueueWorker` abstract base**

```kotlin
package com.jobhunter.core.worker

import com.jobhunter.core.domain.QueueState
import com.jobhunter.core.repository.ProcessingQueueRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import kotlin.math.min
import kotlin.math.pow

private val log = KotlinLogging.logger {}

abstract class QueueWorker(
    private val inputState: QueueState,
    private val outputState: QueueState,
    private val queue: ProcessingQueueRepository,
    txManager: PlatformTransactionManager,
    private val maxAttempts: Int,
    private val baseBackoffSeconds: Long = 5,
    private val maxBackoffSeconds: Long = 80,
) {
    private val tx = TransactionTemplate(txManager)

    /**
     * Subclasses override this with the actual work for one row.
     * Throw to indicate failure; the framework handles retries and state.
     */
    abstract fun process(jobPostingId: Long)

    /** Claim a small batch and process each row in its own transaction. */
    fun runOnce(batchSize: Int = 5) {
        val claimed = tx.execute { queue.claimNext(inputState.name, batchSize) } ?: return
        for (row in claimed) {
            handleOne(row.id!!, row.jobPostingId)
        }
    }

    private fun handleOne(queueId: Long, jobPostingId: Long) {
        try {
            process(jobPostingId)
            tx.executeWithoutResult {
                val r = queue.findById(queueId).get()
                r.state = outputState
                r.attempts = 0
                r.lastError = null
                r.nextAttemptAt = null
                r.updatedAt = Instant.now()
                queue.save(r)
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
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :backend:core:test --tests "com.jobhunter.core.worker.QueueWorkerTest"
```

Expected: PASS — all three tests.

- [ ] **Step 5: Commit**

```bash
git add backend/core/src/main/kotlin/com/jobhunter/core/worker/QueueWorker.kt \
        backend/core/src/test/kotlin/com/jobhunter/core/worker/QueueWorkerTest.kt
git commit -m "feat: add QueueWorker abstract base with retry and backoff"
```

---

## Task 18: QueueNotifier (Postgres LISTEN/NOTIFY) + test

**Files:**
- Create: `backend/core/src/main/kotlin/com/jobhunter/core/worker/QueueNotifier.kt`
- Create: `backend/core/src/test/kotlin/com/jobhunter/core/worker/QueueNotifierTest.kt`

This wraps Postgres `NOTIFY` so workers can wake each other when they advance a row.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.jobhunter.core.worker

import com.jobhunter.core.AbstractRepositoryTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertEquals

class QueueNotifierTest : AbstractRepositoryTest() {
    @Autowired lateinit var jdbc: JdbcTemplate

    @Test
    fun `notify executes against postgres without error`() {
        val notifier = QueueNotifier(jdbc)
        // We cannot easily LISTEN in the same test connection, but we can verify
        // NOTIFY is a no-op (returns void) when there are no listeners — failure
        // would be a SQL exception.
        notifier.notify("queue_event")
        // A sanity SELECT confirms the connection is still healthy.
        assertEquals(1, jdbc.queryForObject("SELECT 1", Int::class.java))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :backend:core:test --tests "com.jobhunter.core.worker.QueueNotifierTest"
```

Expected: FAIL — `QueueNotifier` does not exist.

- [ ] **Step 3: Implement `QueueNotifier`**

```kotlin
package com.jobhunter.core.worker

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class QueueNotifier(private val jdbc: JdbcTemplate) {
    /** Send a Postgres NOTIFY on the given channel. No payload. */
    fun notify(channel: String) {
        require(channel.matches(Regex("[a-z_][a-z0-9_]*"))) {
            "channel must be a safe identifier; got '$channel'"
        }
        jdbc.execute("NOTIFY $channel")
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :backend:core:test --tests "com.jobhunter.core.worker.QueueNotifierTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/core/src/main/kotlin/com/jobhunter/core/worker/QueueNotifier.kt \
        backend/core/src/test/kotlin/com/jobhunter/core/worker/QueueNotifierTest.kt
git commit -m "feat: add QueueNotifier wrapper around Postgres NOTIFY"
```

---

## Task 19: OllamaHealthIndicator + test

**Files:**
- Create: `backend/app/src/main/kotlin/com/jobhunter/app/health/OllamaHealthIndicator.kt`
- Create: `backend/app/src/test/kotlin/com/jobhunter/app/health/OllamaHealthIndicatorTest.kt`

Pings the chat model with a 1-token prompt; UP if it responds, DOWN otherwise. Health checks are part of the dashboard later.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.jobhunter.app.health

import com.jobhunter.core.client.LlmClient
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.boot.actuate.health.Status
import kotlin.test.assertEquals

class OllamaHealthIndicatorTest {

    @Test
    fun `UP when llm responds`() {
        val llm = mockk<LlmClient>()
        every { llm.chat(any(), any()) } returns "ok"
        val health = OllamaHealthIndicator(llm).health()
        assertEquals(Status.UP, health.status)
    }

    @Test
    fun `DOWN when llm throws`() {
        val llm = mockk<LlmClient>()
        every { llm.chat(any(), any()) } throws RuntimeException("connection refused")
        val health = OllamaHealthIndicator(llm).health()
        assertEquals(Status.DOWN, health.status)
        assertEquals("connection refused", health.details["error"])
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :backend:app:test --tests "com.jobhunter.app.health.OllamaHealthIndicatorTest"
```

Expected: FAIL — `OllamaHealthIndicator` does not exist.

- [ ] **Step 3: Implement `OllamaHealthIndicator`**

```kotlin
package com.jobhunter.app.health

import com.jobhunter.core.client.LlmClient
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component

@Component("ollama")
class OllamaHealthIndicator(private val llm: LlmClient) : HealthIndicator {
    override fun health(): Health = try {
        llm.chat(system = "Reply with the single word OK.", user = "ping")
        Health.up().build()
    } catch (e: Exception) {
        Health.down().withDetail("error", e.message ?: "unknown").build()
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :backend:app:test --tests "com.jobhunter.app.health.OllamaHealthIndicatorTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/app/src/main/kotlin/com/jobhunter/app/health/OllamaHealthIndicator.kt \
        backend/app/src/test/kotlin/com/jobhunter/app/health/OllamaHealthIndicatorTest.kt
git commit -m "feat: add Ollama health indicator"
```

---

## Task 20: Smoke test — full app boots and health endpoint responds

**Files:**
- Create: `backend/app/src/test/kotlin/com/jobhunter/app/SmokeTest.kt`

Validates that the entire wiring (Spring Boot + Spring AI + Spring Data JPA + Flyway + Testcontainers Postgres + mocked Ollama) boots cleanly and `/actuator/health` returns 200.

- [ ] **Step 1: Write the smoke test**

```kotlin
package com.jobhunter.app

import com.jobhunter.core.client.LlmClient
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SmokeTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("pgvector/pgvector:pg16")
            .withDatabaseName("jobhunter")
            .withUsername("jobhunter")
            .withPassword("jobhunter")

        @DynamicPropertySource
        @JvmStatic
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            // Disable real Ollama auto-config; we provide a mock LlmClient.
            registry.add("spring.ai.ollama.base-url") { "http://disabled" }
        }
    }

    @LocalServerPort var port: Int = 0
    @Autowired lateinit var rest: RestTemplateBuilder

    @TestConfiguration
    class MockLlm {
        @Bean
        @Primary
        fun llmClient(): LlmClient = mockk<LlmClient>().apply {
            every { chat(any(), any()) } returns "ok"
            every { chatStructured(any(), any()) } returns "{}"
        }
    }

    @Test
    fun `actuator health is up`() {
        val template = rest.build()
        val response = template.getForEntity("http://localhost:$port/actuator/health", String::class.java)
        assertEquals(200, response.statusCode.value())
        assertTrue(response.body!!.contains("\"status\":\"UP\""))
    }
}
```

- [ ] **Step 2: Run the smoke test**

```bash
./gradlew :backend:app:test --tests "com.jobhunter.app.SmokeTest"
```

Expected: PASS — `actuator health is up`. The container takes ~10-20s to boot on first run.

- [ ] **Step 3: Run the full test suite**

```bash
./gradlew test
```

Expected: ALL tests pass across both modules.

- [ ] **Step 4: Commit**

```bash
git add backend/app/src/test/kotlin/com/jobhunter/app/SmokeTest.kt
git commit -m "test: add full-app smoke test"
```

---

## Task 21: Manual end-to-end verification + final README polish

**Files:**
- Modify: `README.md`

Final dev-loop check, then a slightly fuller README so anyone picking this up next plan understands the layout.

- [ ] **Step 1: Bring up sidecars**

```bash
docker compose up -d postgres ollama
docker compose ps
```

Expected: both services healthy.

- [ ] **Step 2: Run the app from the command line (simulating IntelliJ)**

```bash
./gradlew :backend:app:bootRun
```

Watch the logs — you should see:
- Flyway: `Successfully applied 1 migration to schema "public"`
- Spring AI: Ollama chat model + embedding model registered
- `Started JobHunterApplication in <N> seconds`

- [ ] **Step 3: Hit the health endpoint**

In a separate terminal:

```bash
curl -s http://localhost:8080/actuator/health | jq
```

Expected output:

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP", "details": { "database": "PostgreSQL", ... } },
    "diskSpace": { "status": "UP", ... },
    "ollama": { "status": "UP" },
    "ping": { "status": "UP" }
  }
}
```

If `ollama` is `DOWN`, verify `docker compose ps` shows the Ollama container running and that you've pulled at least one model (`docker exec jobhunter-ollama ollama list`).

- [ ] **Step 4: Stop the app and update `README.md`**

```markdown
# AI Job Hunter

Personal job-search and CV-matching system. Local-only, Hebrew + English, Kotlin backend, React frontend (later plan).

See [`docs/superpowers/specs/2026-04-30-ai-job-hunter-design.md`](docs/superpowers/specs/2026-04-30-ai-job-hunter-design.md) for the full design.

## Project layout

```
ai-job-hunter/
├── backend/
│   ├── core/    # shared domain, queue framework, LLM and embedding clients
│   └── app/     # @SpringBootApplication, REST endpoints, health, scheduling
├── docker-compose.yml
└── docs/superpowers/{specs,plans}/
```

## Dev setup

1. **Start sidecars:** `docker compose up -d postgres ollama`
2. **One-time model pull** (this can take a while; the 32B model is ~19GB):
   ```bash
   docker exec jobhunter-ollama ollama pull bge-m3
   docker exec jobhunter-ollama ollama pull aya-expanse:32b
   ```
3. **Run app:** Open in IntelliJ; run `JobHunterApplication.kt`. Or from CLI: `./gradlew :backend:app:bootRun`.
4. **Verify:** `curl http://localhost:8080/actuator/health`

## Tests

```bash
./gradlew test
```

Repository tests use Testcontainers, so Docker must be running.

## Stack

Kotlin 2.0 · Spring Boot 3.4 · Spring AI 1.0 · Postgres 16 + pgvector · Ollama (local LLM) · Flyway · Testcontainers · MockK · JUnit 5
```

- [ ] **Step 5: Commit and tag the milestone**

```bash
git add README.md
git commit -m "docs: expand README with project layout and dev setup"
git tag plan-1-complete
```

---

## End of Plan 1

**At this point:**

- [x] Project boots from IntelliJ or `bootRun`.
- [x] Postgres + Ollama run as sidecars in docker-compose.
- [x] All 7 entities + 7 repositories exist and are integration-tested.
- [x] Schema migration applied via Flyway.
- [x] pgvector roundtrip verified end-to-end.
- [x] LlmClient and EmbeddingClient wrappers ready for downstream modules.
- [x] QueueWorker base class with retry + backoff ready.
- [x] QueueNotifier ready.
- [x] Health endpoint reports the full stack.
- [x] Full test suite passes.

**Next plan: Plan 2 — First source: IMAP ingestion** (will create `backend/ingestion`, an IMAP client adapter, the first `JobSource` config rows, and an admin endpoint to trigger an ingestion run).
