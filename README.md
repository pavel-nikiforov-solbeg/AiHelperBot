# AiHelperBot

Spring Boot 3.2.5 / Java 17 RAG (Retrieval-Augmented Generation) service for a Performance Management chatbot.

## Prerequisites

- Java 17+
- PostgreSQL
- Ollama running locally (`http://localhost:11434`) with `nomic-embed-text` model
- Azure Blob Storage account (optional — degraded mode if absent)

## Environment Variables

Copy `.env.example` to `.env` and fill in the values:

| Variable | Required | Description |
|----------|----------|-------------|
| `DB_PASSWORD` | Yes | PostgreSQL password for `bot_user` |
| `OPEN_ROUTER_API_KEY` | Yes | API key for the LLM provider (OpenRouter) |
| `LLM_BASE_URL` | No | LLM API base URL (default: `https://openrouter.ai/api/v1`) |
| `LLM_MODEL` | Yes | Model identifier (e.g. `openai/gpt-4o`) |
| `LLM_HTTP_REFERER` | No | HTTP Referer header sent with LLM requests |
| `AZURE_BLOB_URL` | No | Azure Blob Storage connection string (degraded mode if absent) |
| `GUIDE_URL` | No | URL to the PDF user guide passed in LLM payload |

## Commands

```cmd
# Build
mvnw.cmd clean package

# Run
mvnw.cmd spring-boot:run

# Run all tests
mvnw.cmd test

# Run a single test class
mvnw.cmd test -Dtest=ClassName
```

## REST API

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/ask` | Ask a question; returns LLM answer |
| POST | `/api/v1/botfeedback` | Save thumbs-up/down feedback |
| GET | `/api/v1/botfeedback/{id}` | Retrieve feedback by ID |
| GET | `/api/v1/bot/topics` | Get bot intro text and topic list |
