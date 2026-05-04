# AI Job Hunter

Personal job-search and CV-matching system. Local-only, Hebrew + English, Kotlin + Spring Boot backend, React frontend.

See [`docs/superpowers/specs/2026-04-30-ai-job-hunter-design.md`](docs/superpowers/specs/2026-04-30-ai-job-hunter-design.md) for the full design.

## What it does

1. Pulls job postings from your Gmail job-alert emails (LinkedIn, Indeed, Glassdoor) and from AllJobs / JobMaster scrapers.
2. Parses them, classifies into categories, embeds with bge-m3.
3. Matches each posting against your CV using cosine + LLM rerank.
4. Surfaces top matches in a React review queue.
5. On click: drafts a language-matched cover letter; you review/edit; one click sends with your CV attached.

## Project layout

```
ai-job-hunter/
├── backend/
│   ├── core/         # shared domain, queue framework, LLM and embedding clients
│   ├── ingestion/    # IMAP + alert-email parsers, AllJobs/JobMaster scrapers
│   ├── processing/   # parse / classify / embed workers; queue listener
│   ├── matching/     # CV upload, structured summary, two-stage matching worker
│   ├── delivery/     # cover letter drafts, SMTP send, audit trail
│   └── app/          # @SpringBootApplication, REST endpoints, health, scheduling
├── frontend/         # React 19 + Vite, review queue UI
├── docker-compose.yml
└── docs/superpowers/{specs,plans}/
```

## Stack

Kotlin 2.0 · Spring Boot 3.4 · Spring AI 1.0 · Postgres 16 + pgvector · Ollama (aya-expanse:32b + bge-m3) · React 19 · Vite · Vitest · Playwright · GreenMail · Testcontainers · MockK · JUnit 5 · Jsoup · Tika

## Dev setup

1. **Start sidecars:** `docker compose up -d postgres ollama`
2. **One-time model pull** (the 32B model is ~19GB):
   ```bash
   docker exec jobhunter-ollama ollama pull bge-m3
   docker exec jobhunter-ollama ollama pull aya-expanse:32b
   ```
3. **Configure secrets** — copy `backend/app/src/main/resources/application-local.yml.example` to `application-local.yml`, fill in IMAP + SMTP credentials.
4. **Run backend** in IntelliJ (`JobHunterApplication.kt`) or via `./gradlew :app:bootRun`.
5. **Run frontend:** `cd frontend && npm install && npm run dev`.
6. Open `http://localhost:5173`.

## Setting up IMAP (Gmail)

1. Enable 2FA on your Gmail account.
2. Generate a Google App Password: https://myaccount.google.com/apppasswords
3. Fill in `jobhunter.imap.username` and `jobhunter.imap.password` in `application-local.yml`.
4. Apply Gmail filters so your job-alert emails stay in INBOX (we filter by sender domain, e.g. `@linkedin.com`).
5. The scheduler runs every 15 minutes. Trigger immediately with `curl -X POST "http://localhost:8080/api/admin/ingestion/run-now?source=IMAP_LINKEDIN_ALERTS"`.

## Uploading your CV

```bash
curl -X POST -F "file=@cv.pdf" -F "label=default" http://localhost:8080/api/cv
```

The first upload becomes the active CV. Each subsequent upload deactivates the previous active CV (history is preserved). Once an active CV exists, the `MatchWorker` scores `EMBEDDED` postings against it; surfaceable matches appear at `GET /api/matches`.

## Sending CVs

The send flow is two-step:

1. `POST /api/matches/{id}/draft` — generates a cover letter (in the posting's language). Returns subject + body.
2. `POST /api/matches/{id}/send` — body `{"subject": "...", "body": "..."}` (optional overrides). Sends via SMTP and writes `email_send_record`.
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

## Tests

```bash
./gradlew test                # backend (all six modules)
cd frontend && npm test       # frontend unit tests
cd frontend && npm run e2e    # Playwright smoke test
```

Repository tests use Testcontainers, so Docker must be running.

## Production / always-on

```bash
./frontend/build-and-copy.sh
./gradlew :app:bootJar
java -jar backend/app/build/libs/app-0.1.0-SNAPSHOT.jar
```

Now both UI and API are served from `http://localhost:8080`.
