# AI Job Hunter

Personal job-search and CV-matching system. See `docs/superpowers/specs/2026-04-30-ai-job-hunter-design.md` for full design.

## Dev setup

1. `docker compose up -d postgres ollama`
2. First-time only: `docker exec -it jobhunter-ollama ollama pull aya-expanse:32b` then `bge-m3`
3. Open in IntelliJ; run `JobHunterApplication.kt`
4. Browse `http://localhost:8080/actuator/health`

## Stack

Kotlin · Spring Boot · Postgres + pgvector · Ollama (local LLM) · React (later plan)
