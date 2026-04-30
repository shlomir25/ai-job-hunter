# AI Job Hunter — Design Spec

**Status:** Draft v1
**Date:** 2026-04-30
**Owner:** Shlomi Rahimi
**Audience:** This spec is the input to an implementation plan. It describes WHAT the system does and HOW it is structured, not the per-task implementation order.

---

## 1. Overview

A personal, locally-run system that continuously ingests job postings from multiple sources, classifies them by role category, scores them against the user's CV, and presents a one-click review queue for sending tailored cover letters to job-listing contact emails. Hebrew + English. No cloud LLMs.

### Goals

- Ingest job postings from a small set of sources (email alerts + web scrapers) on a recurring cadence.
- Parse, classify, embed, and score each posting with local LLMs.
- Surface top matches against the user's CV in a web UI for human review.
- Generate language-matched cover letters; user reviews and edits before sending.
- Send the user's CV via SMTP to the contact email extracted from the posting.
- Maintain a complete audit trail of what was sent, when, and why it matched.

### Non-goals (v1)

- Multi-tenant / multi-user support.
- Cloud-hosted deployment, public URLs, OAuth.
- Auto-sending without human review.
- LinkedIn or WhatsApp scraping (legal/feasibility).
- Cloud LLM fallback.
- Mobile app.
- Fine-tuning or RAG.

---

## 2. Constraints

| Constraint | Source | Implication |
|---|---|---|
| Backend in Kotlin + Spring Boot, Gradle | User preference | JPA, Spring AI, spring-retry, spring-data |
| Frontend in React + JavaScript | User preference | Vite dev server, build to Spring static resources for prod |
| Postgres only | User preference | Use pgvector extension, JSONB, native arrays |
| LLM must be local | User preference | Ollama sidecar; no Anthropic/OpenAI calls |
| Hebrew + English postings | User's market (Israel) | Multilingual model required (aya-expanse-32B + bge-m3) |
| Apple Silicon, 32-64GB unified memory | User hardware | 32B Q4-quantized model is the sweet spot |
| Personal use, single user | User intent | No auth on localhost; SQLite-grade simplicity tolerated |
| Spring Boot runs from IntelliJ in dev | User preference | Postgres + Ollama via `docker compose`; app run as IntelliJ task |
| ToS-respecting | Legal | No LinkedIn/Glassdoor scraping; rely on email alerts for those sources |

---

## 3. Architecture (high level)

```
┌─────────────────────────────────────────────────────────────┐
│ Mac (local-only)                                            │
│                                                             │
│  ┌───────────────┐         ┌──────────────────────────┐     │
│  │  React UI     │ ──────▶ │  Spring Boot app         │     │
│  │ (Vite, :5173) │ <────── │  (:8080) — IntelliJ run  │     │
│  └───────────────┘         │                          │     │
│                            │  modules:                │     │
│                            │  ├ core                  │     │
│                            │  ├ ingestion             │     │
│                            │  ├ processing            │     │
│                            │  ├ matching              │     │
│                            │  ├ delivery              │     │
│                            │  └ app (web + wiring)    │     │
│                            └──┬──────┬──────┬─────────┘     │
│                               │      │      │               │
│                          ┌────▼─┐ ┌──▼─┐ ┌──▼─────┐         │
│                          │ PG + │ │Olla│ │ SMTP   │         │
│                          │pgvec │ │ ma │ │(Gmail) │         │
│                          │:5432 │ │:114│ │ via    │         │
│                          │      │ │ 34 │ │ app pw │         │
│                          └──────┘ └────┘ └────────┘         │
│                                                             │
│  External: IMAP (Gmail) for alert emails;                   │
│            HTTPS scrapers for AllJobs, JobMaster.           │
└─────────────────────────────────────────────────────────────┘
```

**Communication boundaries:**

- **Spring Boot ↔ Postgres:** JDBC + JPA + Flyway for migrations.
- **Spring Boot ↔ Ollama:** HTTP via Spring AI's `OllamaChatModel` and `OllamaEmbeddingModel`.
- **Spring Boot ↔ React UI:** REST + JSON. CORS configured for `localhost:5173` in dev.
- **Spring Boot ↔ external sources:** IMAP (JavaMail / Jakarta Mail), HTTPS scraping (Jsoup or Playwright-Java per scraper).
- **Spring Boot ↔ SMTP:** JavaMail via Spring `JavaMailSender`.
- **Inter-module:** modules communicate by advancing rows through `processing_queue` states. No direct service-to-service calls across modules.

**Run model:**

- Dev: `docker compose up postgres ollama` for sidecars, IntelliJ runs `JobHunterApplication.kt`, `npm run dev` for React.
- Prod (single-host): `./gradlew bootRun` or packaged jar; React built into Spring static resources.
- Cron triggers ingestion every 15 minutes via Spring `@Scheduled`.

---

## 4. Module Breakdown

Single Gradle multi-module project. Each module has the same MVC-aligned internal structure (with the layers it actually needs).

```
ai-job-hunter/
├── settings.gradle.kts
├── build.gradle.kts
├── docker-compose.yml         # postgres + ollama
├── frontend/                  # React (Vite)
│   └── src/...
└── backend/
    ├── app/                   # @SpringBootApplication, controllers, security, scheduling wiring
    ├── core/                  # shared domain, queue framework, LLM client wrappers
    ├── ingestion/             # IMAP source + HTTP scrapers
    ├── processing/            # parse, classify, embed workers
    ├── matching/              # CV ↔ posting scoring
    └── delivery/              # cover-letter draft + SMTP send
```

### 4.1 Per-module internal layout (consistent MVC layering)

```
backend/<module>/
└── src/main/kotlin/com/jobhunter/<module>/
    ├── controller/      # @RestController (only where exposed)
    ├── service/         # @Service — business logic, @Transactional
    ├── repository/      # @Repository — Spring Data JPA interfaces
    ├── domain/          # @Entity / value classes
    ├── dto/             # request/response DTOs (entities never leak past service)
    ├── worker/          # queue consumers (background)
    ├── client/          # outbound integrations (Ollama, IMAP, SMTP, scrapers)
    └── config/          # @Configuration — beans, properties
```

### 4.2 Per-module presence and responsibility

| Module | controller | service | repository | domain | dto | worker | client | Responsibility |
|---|---|---|---|---|---|---|---|---|
| `core` | — | infrastructure-only | base classes | shared | — | — | LLM, embed | Cross-cutting infrastructure: shared domain (e.g., `ProcessingQueueRow`, base entity types), the queue worker framework, the `LlmClient`/`EmbeddingClient` wrappers around Spring AI, common Postgres config, shared error types. No feature-level business logic — feature modules depend on `core`, never the reverse. |
| `ingestion` | admin | ✓ | ✓ | ✓ | ✓ | — | IMAP, scrapers | Turns external sources into `JobPosting` rows in state `INGESTED`. |
| `processing` | — | ✓ | — | — | ✓ | ✓ | — | ParseWorker, ClassifyWorker, EmbedWorker. Advances `INGESTED → EMBEDDED`. |
| `matching` | — | ✓ | ✓ | ✓ | ✓ | ✓ | — | MatchWorker. Two-stage scoring. Produces `Match` rows in `READY_FOR_REVIEW`. |
| `delivery` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | SMTP | DraftWorker (cover letter), send endpoint, `email_send_record`. |
| `app` | web | — | — | — | ✓ | — | — | `@SpringBootApplication`, REST endpoints for the React UI, schedulers, security config. Pure wiring. |

### 4.3 Layer rules (consistent across all modules)

- **Controllers** are thin. Validation, auth, status codes only. Take/return DTOs. Never touch repositories.
- **Services** hold business logic and `@Transactional` boundaries. Use repositories and clients. Cross-module service calls are avoided in favor of queue-based handoff.
- **Repositories** are Spring Data JPA `JpaRepository<Entity, ID>`. Entities never escape past the service boundary; services map to DTOs.
- **Workers** are `@Component` background consumers. They are dumb dispatchers — call the service to do the work. Always idempotent.
- **Clients** wrap external systems. One interface + real impl + fake impl (for tests).
- **DTOs ≠ Entities.** Mapping via Kotlin extension functions (cheap) or MapStruct (if growth justifies it).
- **Admin endpoints** in `ingestion` and `delivery` allow manual triggering during dev (e.g., `POST /api/admin/ingestion/run-now`).

---

## 5. Data Flow

### 5.1 State machine (per posting)

The `processing_queue` table holds one row per posting in flight. State transitions are performed by exactly one worker, in a single transaction. Crash mid-stage = row stays at the previous state, retried on next worker tick.

```
            ┌────────────────────────────────────────┐
            │                                        │
            ▼                                        │ retry (exp. backoff)
    ┌──────────────┐                                 │
    │  INGESTED    │  ◀── ingestion module writes ───┘
    └──────┬───────┘
           │  ParseWorker:  raw → structured fields (LLM + regex for email)
           ▼
    ┌──────────────┐
    │   PARSED     │
    └──────┬───────┘
           │  ClassifyWorker: assign category labels (LLM)
           ▼
    ┌──────────────┐
    │  CLASSIFIED  │  ── if no monitored category match: → OUT_OF_SCOPE (terminal)
    └──────┬───────┘
           │  EmbedWorker: bge-m3 → vector(1024)
           ▼
    ┌──────────────┐
    │   EMBEDDED   │
    └──────┬───────┘
           │  MatchWorker: cosine filter → LLM rerank → on success creates a Match row
           ▼
    ┌──────────────┐
    │   MATCHED    │  ← processing_queue terminal-success
    └──────────────┘
           │  (in same transaction, MatchWorker creates a Match row in state READY_FOR_REVIEW)
           ▼
    ════════════════════════════════════════════════════════════════════
    Boundary: states above live on processing_queue.state.
              states below live on match.state.
    ════════════════════════════════════════════════════════════════════
           ▼
    ┌──────────────────────┐
    │  READY_FOR_REVIEW    │  ← user sees this in UI
    └──────┬───────────────┘
           │  user clicks "Send"
           ▼
    ┌──────────────────────┐
    │  DRAFTED             │  ← cover letter generated; user reviews/edits
    └──────┬───────────────┘
           │  user clicks "Confirm send"
           ▼
    ┌──────────────────────┐
    │  SENT                │  ← email_send_record written (match terminal)
    └──────────────────────┘

    processing_queue side-branch terminals (no Match row created):
        OUT_OF_SCOPE  — classified into a non-monitored category (analytics only)
        IRRELEVANT    — classifier returned no labels, OR cosine < threshold,
                        OR LLM relevance score < threshold
        FAILED        — exceeded retry limit (visible in UI for re-queue)

    match side-branch terminals (Match row exists, state ≠ SENT):
        SKIPPED       — user dismissed match
        SEND_FAILED   — SMTP rejected; user must explicitly retry
```

The two state machines are deliberately split: `processing_queue` tracks ingestion plumbing (fire-and-forget once a Match exists), `match` tracks the durable user-facing lifecycle. They're advanced in the same transaction at the queue→match boundary, so they can never disagree.

**Worker claiming pattern:**

```sql
SELECT * FROM processing_queue
WHERE state = :targetState
  AND (next_attempt_at IS NULL OR next_attempt_at <= now())
ORDER BY id
LIMIT :batch
FOR UPDATE SKIP LOCKED;
```

`SKIP LOCKED` lets multiple worker threads run safely without coordination overhead.

### 5.2 Trigger paths

**Push (Postgres `LISTEN/NOTIFY`):** Each worker writes a row, then `NOTIFY queue_event`. Other workers `LISTEN` and immediately attempt to claim the next stage's input. Result: a posting goes from `INGESTED` to `READY_FOR_REVIEW` in seconds without polling.

**Poll (safety net):** Each worker also has a `@Scheduled(fixedDelay = 60s)` task that scans for rows in its input state — covers the case where the app was down when a `NOTIFY` fired.

### 5.3 User-driven flow

```
GET  /api/matches?state=READY_FOR_REVIEW                — top matches (sorted by llm_score)
GET  /api/matches/{id}                                  — full detail (posting, CV, reasoning)
POST /api/matches/{id}/draft                            — trigger DraftWorker; returns draft email
POST /api/matches/{id}/send  { body, subject }          — final SMTP send
POST /api/matches/{id}/skip                             — mark SKIPPED
GET  /api/admin/dashboard                               — health, queue stats, source status
GET  /api/admin/queue?state=FAILED                      — failed rows for inspection
POST /api/admin/queue/{id}/requeue                      — reset state, clear error
POST /api/admin/ingestion/run-now?source={code}         — manual trigger
GET  /api/cv                                            — list active CVs
POST /api/cv  (multipart)                               — upload new CV
```

---

## 6. Data Model

Postgres 16 + `pgvector`. Spring Data JPA + Hibernate, with `pgvector-java` for the `vector` type. Field naming: `snake_case` in DB, `camelCase` in Kotlin.

### 6.1 `category` enum (broad — covers user's needs + future expansion)

```sql
CREATE TYPE category AS ENUM (
    -- Software / Engineering
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
    -- Non-engineering
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
```

`monitored_categories` is configured in `application.yml` (default: software-related set for v1):

```yaml
jobhunter:
  monitored-categories:
    - SOFTWARE_BACKEND
    - SOFTWARE_FULLSTACK
    - DEVOPS
    - SRE
    - PLATFORM
```

Postings classified outside this set move to `OUT_OF_SCOPE` (terminal, kept for analytics; no embedding/matching cost).

### 6.2 `job_source`

| Column | Type | Notes |
|---|---|---|
| `id` | bigserial PK | |
| `code` | varchar(50) UNIQUE | `IMAP_LINKEDIN_ALERTS`, `SCRAPER_ALLJOBS` |
| `type` | varchar(20) | `IMAP` \| `SCRAPER` |
| `enabled` | boolean | toggle without deleting |
| `config` | jsonb | per-type config (IMAP filter, scraper URL/selectors) |
| `last_run_at` | timestamptz | |
| `last_run_status` | varchar(20) | `OK` \| `FAILED` |
| `last_run_error` | text | truncated stack/message if failed |

### 6.3 `job_posting`

| Column | Type | Notes |
|---|---|---|
| `id` | bigserial PK | |
| `source_id` | bigint FK → job_source | |
| `external_id` | varchar(255) | Source-stable ID; content-hash for emails |
| `source_url` | text | |
| `raw_text` | text | normalized, HTML stripped |
| `raw_html` | text | preserved for re-parsing |
| `title` | varchar(500) | populated by ParseWorker |
| `company` | varchar(255) | |
| `location` | varchar(255) | |
| `is_remote` | boolean | |
| `language` | char(2) | `he` or `en`; detected by ParseWorker |
| **`contact_email`** | varchar(255) | **address to send CV to; regex-extracted with LLM fallback** |
| `apply_url` | text | fallback when no email |
| `description` | text | |
| `requirements` | text | |
| `salary_text` | varchar(255) | raw, not parsed |
| `categories` | category[] | multi-label |
| `posted_at` | timestamptz | from posting if available |
| `captured_at` | timestamptz | when we ingested it |

Constraints:
- `UNIQUE (source_id, external_id)` — natural dedup
- `INDEX idx_posting_categories ON job_posting USING GIN (categories)`
- `INDEX idx_posting_captured_at ON job_posting (captured_at DESC)`
- `INDEX idx_posting_contact_email ON job_posting (contact_email) WHERE contact_email IS NOT NULL`

### 6.4 `posting_embedding` (separate table)

| Column | Type | Notes |
|---|---|---|
| `job_posting_id` | bigint PK FK | one-to-one |
| `embedding` | vector(1024) | bge-m3 |
| `model_name` | varchar(100) | e.g. `bge-m3:latest`; allows re-embed on model change |
| `created_at` | timestamptz | |

Index: `USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64)`.

### 6.5 `cv`

| Column | Type | Notes |
|---|---|---|
| `id` | bigserial PK | |
| `label` | varchar(100) | `default`, `kotlin-focused`, `hebrew-version` |
| `file_name` | varchar(255) | |
| `mime_type` | varchar(100) | `application/pdf` \| `application/vnd.openxmlformats-...` |
| `file_bytes` | bytea | the original |
| `parsed_text` | text | extracted via Apache Tika |
| `embedding` | vector(1024) | bge-m3 |
| `structured_summary` | jsonb | LLM-extracted: `{skills[], years_total_experience, languages[], past_roles[], education}` |
| `is_active` | boolean | only one active at a time (enforced by partial unique index) |
| `created_at` | timestamptz | |

Constraint: `CREATE UNIQUE INDEX cv_one_active ON cv (is_active) WHERE is_active = true;`

### 6.6 `processing_queue`

| Column | Type | Notes |
|---|---|---|
| `id` | bigserial PK | |
| `job_posting_id` | bigint FK | |
| `state` | varchar(30) | `INGESTED` \| `PARSED` \| `CLASSIFIED` \| `EMBEDDED` \| `MATCHED` \| `IRRELEVANT` \| `OUT_OF_SCOPE` \| `FAILED` |
| `attempts` | int | per-stage counter (reset on state advance) |
| `last_error` | text | |
| `next_attempt_at` | timestamptz | for backoff |
| `updated_at` | timestamptz | |
| `created_at` | timestamptz | |

Index: `(state, next_attempt_at)` for the worker claim query.

### 6.7 `match`

| Column | Type | Notes |
|---|---|---|
| `id` | bigserial PK | |
| `job_posting_id` | bigint FK | |
| `cv_id` | bigint FK | |
| `cosine_similarity` | float | 0.0–1.0 |
| `llm_score` | int | 0–100 |
| `llm_reasoning` | jsonb | `{strengths: [...], gaps: [...], summary: "..."}` |
| `state` | varchar(30) | `READY_FOR_REVIEW` \| `DRAFTED` \| `SENT` \| `SKIPPED` \| `SEND_FAILED` |
| `draft_subject` | varchar(500) | nullable until DRAFTED |
| `draft_body` | text | nullable until DRAFTED |
| `created_at` | timestamptz | |
| `updated_at` | timestamptz | |

Constraints:
- `UNIQUE (job_posting_id, cv_id)`
- `INDEX (state, llm_score DESC)`

### 6.8 `email_send_record`

| Column | Type | Notes |
|---|---|---|
| `id` | bigserial PK | |
| `match_id` | bigint UNIQUE FK | one send per match |
| `cv_id` | bigint FK | which CV was attached |
| `to_address` | varchar(255) | |
| `subject` | varchar(500) | |
| `body` | text | exact body sent |
| `attachment_filename` | varchar(255) | |
| `sent_at` | timestamptz | |
| `smtp_message_id` | varchar(255) | |
| `status` | varchar(20) | `SENT` \| `BOUNCED` \| `FAILED` |
| `failure_reason` | text | |

### 6.9 Migrations

Flyway (`backend/core/src/main/resources/db/migration/V1__init.sql`). One file per schema change. Never edit a committed migration. Spring runs them on startup.

---

## 7. LLM and Matching Strategy

### 7.1 Models (Ollama-served)

| Purpose | Model | Size (Q4_K_M) |
|---|---|---|
| Reasoning (parse, classify, match, draft) | `aya-expanse:32b-q4_K_M` | ~19 GB |
| Embeddings | `bge-m3` | ~2 GB |
| Backup small Hebrew model (optional, for parse fallback) | `dictalm2.0-instruct` | ~7 GB |

Both critical models stay loaded simultaneously (~21 GB resident); leaves headroom on a 32 GB machine for Postgres + IDE + browser.

### 7.2 Spring AI integration

- `spring-ai-ollama-spring-boot-starter` for both chat and embedding clients.
- One wrapper class in `core`: `LlmClient` with methods `chatStructured<T>(prompt, schema): T`, `embed(text): FloatArray`. All workers depend on this interface; tests use a fake.
- Single-flight semaphore around 32B reasoning calls (`Semaphore(1)`) — the model can't run two requests in parallel on this hardware.
- Embeddings can run concurrently (different model, lighter).

### 7.3 Five LLM use-sites

**1. ParseWorker — extract structured fields**

```
System: "You extract job-posting fields. Return JSON only matching this schema: <Json schema>.
         Use null for missing fields. Do not invent values."
User:   <raw posting text>
```

Output validated against a `ParsedPostingDto` data class.

**`contact_email` extraction is regex-first:**

```kotlin
val emailRegex = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
val candidates = emailRegex.findAll(rawText).map { it.value }.toList()
val email = pickBestCandidate(candidates)  // prefer non-noreply, prefer same domain as company
```

LLM is invoked only if regex finds zero candidates ("Is there a contact email anywhere in this text? Reply with the email or `null`."). LLM result re-validated against the same regex before save. **Hallucinated emails are the single highest-risk failure mode; this approach forecloses it.**

**2. ClassifyWorker — assign category labels**

```
System: "Classify this job posting. Pick zero or more from: <ENUM list>. 
         Return JSON array. If none apply, return []."
User:   <title + description + requirements>
```

Empty array → `IRRELEVANT` (terminal).
Array contains values not in `monitored_categories` → `OUT_OF_SCOPE` (terminal).
Array intersects `monitored_categories` → continue to `CLASSIFIED`.

**3. EmbedWorker — vectorize**

No reasoning model. `bge-m3.embed(rawText)` → 1024-float vector → `posting_embedding` row.

The same model embeds the user's CV once on upload (also stored on `cv.embedding`).

**4. MatchWorker — two-stage scoring (per posting)**

The worker processes one posting at a time, taking it from `EMBEDDED` to either `READY_FOR_REVIEW` (with a Match row) or `IRRELEVANT` (terminal, no Match row).

Stage 1 — cheap cosine filter against the active CV's embedding:

```sql
SELECT 1 - (pe.embedding <=> :cv_embedding) AS cosine
FROM posting_embedding pe
WHERE pe.job_posting_id = :posting_id;
```

If `cosine < cosine_threshold` (config'd, default 0.40), short-circuit: mark `IRRELEVANT`, do not create a Match row, do not call the LLM. This is the cost-saving filter — it's per-posting, not a top-N batch query.

Stage 2 — LLM relevance pass (only when Stage 1 passes):

```
System: "You evaluate fit between a candidate and a job. Output JSON:
         {score: int 0-100, strengths: [string], gaps: [string], summary: string}"
User:   CV summary: <cv.structured_summary>
        Job: <title, requirements, description>
```

`llm_score >= 60` (configurable) → create `Match` row in `READY_FOR_REVIEW`; processing_queue → terminal-success.
`llm_score < 60` → no Match row created; processing_queue → `IRRELEVANT`. The `cosine_similarity` and rejection reason can optionally be logged for analytics, but no Match exists.

**Match rows only exist for results that passed both gates.** This keeps the `match` table small and means the UI's `state = READY_FOR_REVIEW` query always returns surfaceable rows.

**5. DraftWorker — cover letter**

Triggered when user clicks "Send" on a `READY_FOR_REVIEW` match.

```
System (in posting.language):
        "You write a 4-6 sentence professional cover letter in <he|en>.
         Mention 2-3 specific skill matches. Plain text, no greeting placeholders, no subject line."
User:   CV summary: <cv.structured_summary>
        Job: <title, company, requirements>
        Match strengths: <match.llm_reasoning.strengths>
```

Subject built deterministically (no LLM):
- English: `Application for <title> — <candidate name>`
- Hebrew: `מועמדות לתפקיד <title> — <candidate name>`

Match transitions `READY_FOR_REVIEW → DRAFTED`. UI shows draft for editing before final send.

### 7.4 Reliability rules across all LLM calls

- **JSON-schema validation** on every structured output. Invalid → retry once with stricter prompt; second failure → stage `FAILED`.
- **Per-call timeout:** 120s.
- **Concurrency cap:** at most 1 reasoning call at a time (semaphore). Embeddings unrestricted.
- **No fine-tuning, no RAG.** Direct comparison via embeddings + LLM rerank suffices.
- **Email hallucination guard** (described above) — regex-first, LLM-fallback, regex-validate.

---

## 8. Error Handling, Retries, Observability

### 8.1 Per-stage retry policy

| Stage | Max attempts | Backoff | Notes |
|---|---|---|---|
| Ingestion (IMAP poll) | ∞ per scheduled run | every 15 min | Source-level run; `last_run_status` surfaces failures |
| Ingestion (HTTP scraper) | 3 per run | linear 30s | After 3 failures: `last_run_status=FAILED`, UI alert |
| Parse | 3 | exponential 5s → 80s | Idempotent |
| Classify | 3 | exponential 5s → 80s | Idempotent |
| Embed | 5 | exponential 2s → 60s | Cheapest stage |
| Match | 3 | exponential 5s → 80s | Idempotent |
| Draft | 2 | exponential 10s → 60s | User can re-trigger |
| **Send email** | **0** | — | **No auto-retry; user-initiated only** |

Implementation: `spring-retry`'s `@Retryable` on service methods, with `@Recover` writing the row to `FAILED` on exhaustion.

### 8.2 Failure visibility — three lenses

1. **Per-row:** `processing_queue.last_error` + `attempts` columns. UI's "Failed Rows" page lists them; one-click "re-queue" resets state and clears error.
2. **Per-source:** `job_source.last_run_at` + `last_run_status`. Dashboard widget shows stale or failing sources.
3. **System health:** Spring Boot Actuator `/actuator/health` with custom indicators:
   - `OllamaHealthIndicator` — 1-token ping, must respond within 5s
   - `PostgresHealthIndicator` — built-in
   - `ImapHealthIndicator` — last successful connect within 30 min

`GET /api/admin/dashboard` aggregates all three for the React UI.

### 8.3 Scraper resilience

Two safeguards against silent breakage when sites change HTML:

- **Snapshot tests**: each scraper has a fixture HTML file in `src/test/resources/scrapers/<site>/`. A test parses it and asserts expected fields. Fetching a new fixture → test fails before production breaks.
- **Sentinel field check at runtime**: every scraper run, the first parsed posting must have non-null `title` AND `company`. If either is null on the first row, abort the run, mark source `FAILED`. (Better than ingesting 50 garbage rows.)

### 8.4 SMTP send safety

- **No automatic retry.** Failed send → `match.state = SEND_FAILED` with SMTP error visible in UI. User decides: retry, edit, skip.
- **Pre-send validation:** `to_address` matches email regex AND not in denylist (own address, `noreply@*`, `donotreply@*`). Denylist in `application.yml`.
- **Idempotency:** UI's "Send" submits with `match.id` and current draft hash. Double-click sees `state = SENT` → 409 returned.
- **Audit transactionality:** `email_send_record` row written in same transaction as `match.state = SENT` update. Both or neither.

### 8.5 Logging

- **Logback + `logstash-logback-encoder`** → JSON to `logs/jobhunter.log`. Plain text in IntelliJ console for dev (separate appender).
- **Structured fields on every line:** `posting_id`, `queue_id`, `worker_name`, `stage`, `attempt`, `cv_id`, `match_id` where applicable.
- **Log levels:**
  - `INFO`: stage transitions, source runs starting/ending, sends.
  - `WARN`: retries, parse failures retried, scraper sentinel fails.
  - `ERROR`: terminal failures, SMTP errors, Ollama unavailable, schema-validation exhaustion.

No Prometheus / Grafana / OpenTelemetry. Disproportionate for personal scale.

### 8.6 Secrets

- `application-local.yml` (gitignored) holds Ollama URL, Postgres creds, SMTP app password, IMAP creds.
- CV files in Postgres BYTEA. No filesystem-level encryption — boundary is FileVault (macOS disk encryption).

---

## 9. Testing Strategy

### 9.1 Backend — six test layers

**1. Unit (JUnit 5 + MockK)** — services, prompt builders, mappers, queue state transitions. Mock externals.

**2. Repository (`@DataJpaTest` + Testcontainers)** — fresh `pgvector/pgvector:pg16` Postgres per test class; Flyway migrations applied; pgvector and array operators verified.

**3. Worker (`@SpringBootTest` slice)** — real Postgres, real queue, mocked `LlmClient`. Asserts state transitions: `INGESTED → PARSED` on good input; `FAILED` after N retries on persistent malformed JSON.

**4. LLM prompt (golden-file)** — each prompt class has fixtures: `prompts/parse/sample-1-input.txt` + `prompts/parse/sample-1-expected.json`. Tests use `RecordingLlmClient` (replay-only). **No live Ollama in CI.** Separate `./gradlew testRealLlm` task runs the same suite against real Ollama for model-drift detection — run manually before model upgrades.

**5. Scraper (HTML fixtures)** — `src/test/resources/scrapers/alljobs/posting-1.html` etc. Site changes → fetch new fixture → test fails → fix selectors. Production never breaks silently.

**6. Integration — golden path (`@SpringBootTest` full app)** — GreenMail (in-process IMAP/SMTP), WireMock (HTTP), `RecordingLlmClient`. Trigger ingestion, assert posting reaches `READY_FOR_REVIEW` with expected score; trigger send via API, assert `email_send_record` + GreenMail received message.

### 9.2 Frontend

- **Vitest + React Testing Library** for component tests. **MSW** (Mock Service Worker) for API mocks.
- **Playwright smoke** — single test for review-and-send happy path. Catches build/render breakage.

### 9.3 Coverage targets (informational, not CI-enforced initially)

| Layer | Target | Rationale |
|---|---|---|
| Services | 80%+ | Core logic |
| Workers | 100% | Small surface, critical |
| Repositories | Integration-tested | % is meaningless against DB |
| Controllers | Smoke only | Thin layer; integration test covers them |
| Scrapers | Per-fixture | Per-site fragility |
| LLM prompts | Golden file per shape | Detect regressions |

### 9.4 Test infrastructure deps

- `org.testcontainers:postgresql`, `:junit-jupiter`
- `com.icegreen:greenmail-spring`
- `org.wiremock:wiremock-standalone`
- `io.mockk:mockk`
- `org.springframework.retry:spring-retry` (and its test utilities)

### 9.5 TDD discipline

For non-trivial logic (queue state machine, retry policies, email regex extraction, prompt output validation, scraper parsers), tests come first. CRUD glue and DTO mappers don't need test-first.

---

## 10. Deployment / Dev Setup

### 10.1 Repository layout

```
ai-job-hunter/
├── README.md
├── docker-compose.yml          # postgres + ollama
├── settings.gradle.kts
├── build.gradle.kts
├── docs/
│   └── superpowers/specs/
├── frontend/
│   ├── package.json
│   ├── vite.config.ts
│   └── src/
└── backend/
    ├── app/
    ├── core/
    ├── ingestion/
    ├── processing/
    ├── matching/
    └── delivery/
```

### 10.2 `docker-compose.yml` (sidecars only)

- `postgres:16` with `pgvector/pgvector:pg16` image, volume-mounted data dir, port 5432.
- `ollama/ollama:latest` with named volume for models, port 11434, models pulled on first start via init script.

### 10.3 Dev workflow

1. `docker compose up -d postgres ollama`
2. First-time: `docker exec ollama ollama pull aya-expanse:32b` and `bge-m3`.
3. Open project in IntelliJ; run `JobHunterApplication.kt`.
4. `cd frontend && npm run dev`.
5. Browse `http://localhost:5173`.

### 10.4 Production / always-on (optional, post-v1)

- `./gradlew bootJar` → `backend/app/build/libs/app.jar`.
- React `npm run build` → static assets copied to Spring's `static/`.
- One process serves both API and UI on `:8080`.

---

## 11. Open Questions / Future Work

- **Bounce handling** — v1 does not parse SMTP bounce notifications back into `email_send_record.status`. Manual update only. Future: IMAP-poll a `Sent`/`Bounced` mailbox.
- **Scheduled "best matches" digest** — daily summary email of top new matches. Future enhancement; trivial once core works.
- **WhatsApp groups** — explicitly deferred. If needed later, build a manual "paste this group message" form rather than browser automation.
- **Multi-CV ranking** — system stores multiple CVs but v1 matches only against the active one. Future: select CV per-match.
- **Rate-limited sending** — v1 has no per-day cap. If you find yourself over-applying, add a config'd daily limit and a rolling-window check before send.
- **Translation of Hebrew postings to English (or vice-versa)** — useful if model quality on one language degrades. Not required at 32B.

---

## 12. Appendix

### 12.1 Tech stack summary

| Layer | Choice |
|---|---|
| Language (BE) | Kotlin |
| Framework (BE) | Spring Boot 3.x |
| Build (BE) | Gradle (Kotlin DSL) |
| Persistence | Postgres 16 + pgvector + Flyway + Spring Data JPA + Hibernate |
| LLM | Ollama (local) + Spring AI |
| Reasoning model | `aya-expanse:32b-q4_K_M` |
| Embedding model | `bge-m3` |
| HTTP scraping | Jsoup (default) or Playwright-Java (when JS rendering needed) |
| IMAP | Jakarta Mail |
| SMTP | Spring `JavaMailSender` |
| CV text extraction | Apache Tika |
| Retry | spring-retry |
| Logging | Logback + logstash-logback-encoder |
| FE language | JavaScript / TypeScript (per project preference) |
| FE framework | React |
| FE build | Vite |
| FE testing | Vitest + React Testing Library + MSW |
| BE testing | JUnit 5, MockK, Testcontainers, GreenMail, WireMock |
| E2E | Playwright (single smoke test) |
| Container | Docker (sidecars: postgres, ollama) |

### 12.2 Source list (v1)

| Source | Type | Status |
|---|---|---|
| LinkedIn job alerts (via Gmail) | IMAP | v1 |
| Indeed job alerts (via Gmail) | IMAP | v1 |
| Glassdoor job alerts (via Gmail) | IMAP | v1 |
| AllJobs (alljobs.co.il) | HTTP scraper | v1 |
| JobMaster (jobmaster.co.il) | HTTP scraper | v1 |
| LinkedIn direct scraping | — | **excluded** (ToS) |
| WhatsApp groups | — | **deferred** |

### 12.3 Glossary

- **Posting** — a single job ad as captured from a source.
- **Match** — a CV-to-posting pairing with computed scores. One row per (posting, CV) pair.
- **Monitored category** — a category in the user's interest list. Postings outside this set are classified but not embedded/matched.
- **Worker** — a `@Component` that consumes rows from `processing_queue` in a specific input state and advances them.
- **Source** — a registered ingestion endpoint (one IMAP filter or one scraper).
