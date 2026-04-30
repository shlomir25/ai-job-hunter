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
