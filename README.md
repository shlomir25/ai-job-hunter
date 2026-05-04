# AI Job Hunter

Personal job-search and CV-matching system. Local-only, Hebrew + English, Kotlin backend, React frontend (later plan).

See [`docs/superpowers/specs/2026-04-30-ai-job-hunter-design.md`](docs/superpowers/specs/2026-04-30-ai-job-hunter-design.md) for the full design.

## Project layout

```
ai-job-hunter/
├── backend/
│   ├── core/         # shared domain, queue framework, LLM and embedding clients
│   ├── ingestion/    # IMAP source + alert-email parsers (LinkedIn / Indeed / Glassdoor)
│   ├── processing/   # parse / classify / embed workers; queue listener
│   ├── matching/     # CV upload, structured summary, two-stage matching worker
│   ├── delivery/     # cover letter drafts, SMTP send, audit trail
│   └── app/          # @SpringBootApplication, REST endpoints, health, scheduling
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
3. **Run app:** Open in IntelliJ; run `JobHunterApplication.kt`. Or from CLI: `./gradlew :app:bootRun`.
4. **Verify:** `curl http://localhost:8080/actuator/health`

## Setting up IMAP (Gmail)

1. Enable 2FA on your Gmail account.
2. Generate a Google App Password: https://myaccount.google.com/apppasswords
3. Copy `backend/app/src/main/resources/application-local.yml.example` to `application-local.yml` (it's gitignored).
4. Fill in `jobhunter.imap.username` (your Gmail) and `jobhunter.imap.password` (the app password).
5. Apply Gmail filters/labels so your job-alert emails stay in INBOX (we filter by sender domain, e.g. `@linkedin.com`).
6. The scheduler runs every 15 minutes. Trigger immediately with `curl -X POST "http://localhost:8080/api/admin/ingestion/run-now?source=IMAP_LINKEDIN_ALERTS"`.

## Uploading your CV

```bash
curl -X POST -F "file=@cv.pdf" -F "label=default" http://localhost:8080/api/cv
```

The first upload becomes the active CV. Each subsequent upload deactivates the previous active CV (history is preserved). Once an active CV exists, the `MatchWorker` will score `EMBEDDED` postings against it; surfaceable matches appear at `GET /api/matches`.

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

## Tests

```bash
./gradlew test
```

Repository tests use Testcontainers, so Docker must be running.

## Stack

Kotlin 2.0 · Spring Boot 3.4 · Spring AI 1.0 · Postgres 16 + pgvector · Ollama (local LLM) · Flyway · Testcontainers · MockK · JUnit 5
