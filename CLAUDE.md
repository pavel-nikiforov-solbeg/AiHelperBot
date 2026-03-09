# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Environment (Windows 11)

The primary development environment for this project is **Windows 11**.
When executing commands, generating scripts, or suggesting tooling, prefer **Windows-compatible solutions**.

### Paths

Use **Windows path conventions**.

Examples:

C:\projects\sas-ai-helper
C:\Users\user\AppData\Local\Temp

Avoid Unix-only paths such as:

/home/user/project
/tmp/file

Use these Windows equivalents instead:

| Unix Path      | Windows Equivalent                               |
| -------------- | ------------------------------------------------ |
| `/tmp`         | `%TEMP%` or `C:\Users\<user>\AppData\Local\Temp` |
| `/home/<user>` | `C:\Users\<user>`                                |

### Maven Wrapper

Always use the **Windows Maven wrapper script**:

mvnw.cmd

Examples:

mvnw.cmd clean package
mvnw.cmd spring-boot:run
mvnw.cmd test

Do **not** suggest:

./mvnw

If commands are executed from PowerShell or Git Bash and require explicit path execution, use:

.\mvnw.cmd clean package

### Shell Compatibility

Commands should work in common Windows shells:

* PowerShell
* Windows Terminal
* Git Bash

Avoid Linux-only utilities unless explicitly available in Git Bash.

Prefer:

| Task                 | Windows-friendly command |
| -------------------- | ------------------------ |
| list files           | `dir` or `ls`            |
| remove directory     | `rmdir /s /q`            |
| environment variable | `$env:VAR` (PowerShell)  |
| run local script     | `.\script.ps1`           |

Avoid assuming the presence of:

* bash
* sed
* awk
* grep
* chmod

unless explicitly stated.

### File System Considerations

Windows has several differences that should be respected:

* File paths use `\`
* Case-insensitive filesystem
* `.cmd`, `.bat`, `.ps1` scripts instead of `.sh`
* Executables do not require `chmod +x`

### CLI Tool Recommendations

When suggesting CLI tools, prefer ones that work well on Windows:

Good choices:

* Git
* Maven Wrapper (mvnw.cmd)
* Docker Desktop
* PowerShell
* Windows Terminal
* curl (built-in)

Avoid requiring Linux-only environments unless explicitly using **WSL**.

### Temporary Files

When generating temporary files or cache paths, prefer:

%TEMP%

or

C:\Users<user>\AppData\Local\Temp

instead of `/tmp`.


## Commands

```bash
# Build
./mvnw clean package

# Run
./mvnw spring-boot:run

# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=ClassName

# Run a single test method
./mvnw test -Dtest=ClassName#methodName
```

## Architecture

This is a Spring Boot 3.2.5 / Java 17 RAG (Retrieval-Augmented Generation) service for a Performance Management chatbot. It answers user questions by retrieving relevant chunks from a PDF user guide and sending a composed prompt to an external LLM API.

**Root package**: `com.solbeg.sas.perfmgmnt`

### REST API

| Method | Path | Controller | Description |
|---|---|---|---|
| POST | `/api/v1/ask` | `BotQueryController` | Ask a question; returns LLM answer |
| POST | `/api/v1/botfeedback` | `BotFeedbackController` | Save thumbs-up/down feedback |
| GET | `/api/v1/botfeedback/{id}` | `BotFeedbackController` | Retrieve feedback by ID |
| GET | `/api/v1/bot/topics` | `BotTopicsController` | Get bot intro text and topic list |

Security: CSRF disabled, all endpoints permit all (no authentication required). Authenticated user email is read from `SecurityContextHolder` by `SecurityUtils` when saving feedback.

### RAG Pipeline (core flow)

POST `/api/v1/ask` → `BotQueryController` → `RagService.answer()`

1. `InputValidator` — chain of `InputFilter`s: `QuickInputFilter`, `LanguageDetectorFilter`, `DomainRelevanceChecker`
2. `QueryAnalyzer` — classifies query into `QueryType` enum (`GENERAL`, `DEFINITION`, `TIME_RELATED`, `SUPPORT`, `INBOX`, `PEERS`, `REVIEW_PROCESS`, `REMIND_PEERS`, `GROWTH_PLAN`)
3. `DocumentRetriever` — fetches candidates from `EmbeddingIndexer`, applies type-specific strategies
4. `DocumentRanker` — re-ranks/merges candidates
5. `PromptBuilder` — assembles final prompt string
6. `LlmClient` — POSTs base64-encoded prompt to external LLM API, returns text answer

**DEFINITION fallback**: if the LLM answer contains "not explicitly defined" or "not specified", the query is retried with `GENERAL` strategy.

### Input Validation Pipeline

Filters are Spring beans ordered via `@Order`:
1. `QuickInputFilter` — rejects null/blank input, inputs shorter than `min-length`, and inputs with no Unicode letters
2. `LanguageDetectorFilter` — uses Lingua library; rejects non-English above confidence threshold; skips short inputs (below `short-input-threshold`)
3. `DomainRelevanceChecker` — performs a vector similarity search; rejects off-topic queries below `min-score`

`FilterResult` is a record with `passed` / `reason` fields and factory methods `pass()` / `fail(String)`.

### QueryType & Analysis

`QueryAnalyzer` uses regex pattern matching with this priority order:
`SUPPORT > REVIEW_PROCESS > PEERS > REMIND_PEERS > INBOX > GROWTH_PLAN > TIME_RELATED > DEFINITION > GENERAL`

For `DEFINITION` and `TIME_RELATED` types, the analyzer extracts a specific term (max 3 words) from the query.

### Document Retrieval Strategies

`DocumentRetriever` applies a different strategy per `QueryType`:
- **DEFINITION**: searches glossary section, filters to glossary terms (`GlossaryTerms` static set)
- **TIME_RELATED**: date/deadline/schedule focused search
- **SUPPORT**: error/problem/help focused search + merges support page docs
- **INBOX**: notification/request focused + merges inbox page docs
- **PEERS**: peer selection focused search
- **REVIEW_PROCESS**: manual review scheduling + merges support page docs
- **REMIND_PEERS**: review form submission tab + merges remind-submission page docs
- **GROWTH_PLAN**: differentiates employee vs. manager context + merges growth-plan page docs
- **GENERAL**: broad keyword matching across all documents

### Document Ranking

`DocumentRanker` applies:
- `rankByKeywords()` — word count scoring against query tokens
- `rankByTimeRelevance()` — bonus score for date/deadline pattern matches
- `mergeWith*Page()` — injects specific guide page documents for certain query types

### Vector Store Initialization (`BootstrapIndexRunner`)

Runs once on `ApplicationReadyEvent`. Strategy (in order):
1. Check if already loaded in memory
2. Load from local file cache (`/tmp/sas-rag/vectorstore.bin`)
3. Download from Azure Blob Storage if no local cache
4. Rebuild index if PDF or config changed (SHA-256 hash of PDF + chunk settings stored in `index-metadata.json`)

Rebuild: downloads PDF from Blob → `PdfChunker` splits into chunks → `EmbeddingIndexer` embeds with Ollama (`nomic-embed-text`) → persists `.bin` to local + uploads back to Blob.

Handles degraded mode gracefully if Blob Storage is unavailable.

All subsequent queries hit the **in-memory** `CustomSimpleVectorStore` (extends Spring AI's `SimpleVectorStore`) — no disk/network I/O per request. Documents are indexed by page number for fast filtered lookup.

### Database & Migrations

**Schema management**: Liquibase (changelogs under `src/main/resources/db/changelog/`).
- Master file: `db.changelog-master.yaml`
- Migrations: `changes/001-create-bot-feedback.yaml`

**`bot_feedback` table** (entity: `BotFeedback`):
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | generated from `bot_feedback_seq` (increment 50) |
| `question` | VARCHAR(4000) | NOT NULL |
| `answer` | VARCHAR(10000) | NOT NULL |
| `bot_feedback_type` | VARCHAR(50) | NOT NULL; enum: `LIKE`, `DISLIKE` |
| `employee_email` | VARCHAR(255) | nullable |
| `created_at` | TIMESTAMP | DB-generated default `CURRENT_TIMESTAMP`, read-only |

`BotFeedbackService` truncates answers to 10,000 chars before saving.

### Key Configuration

Config prefix is `solbeg.rag` for RAG properties and `solbeg.bot` for bot properties.

| Property | Purpose |
|---|---|
| `solbeg.rag.storage.blob.url` | Azure Blob Storage connection string |
| `solbeg.rag.llm.base-url` | External LLM API base URL |
| `solbeg.rag.llm.api-key` | LLM API key |
| `solbeg.rag.llm.guide-url` | PDF guide URL passed in LLM request payload |
| `solbeg.rag.llm.mode` | `external` or `internal` |
| `solbeg.rag.llm.max-documents` | Max docs passed to prompt (default 5) |
| `solbeg.rag.guide.chunk-size` / `overlap` | PDF chunking parameters — changing triggers auto-reindex |
| `solbeg.rag.guide.pages.*` | Page offsets into the PDF used by type-specific retrieval strategies |
| `solbeg.rag.validation.quick-filter.min-length` | Min input length (default 2) |
| `solbeg.rag.validation.language-detector.*` | Language detection thresholds |
| `solbeg.rag.validation.domain-checker.*` | Domain relevance thresholds |
| `solbeg.bot.intro-text` | Bot greeting text |
| `solbeg.bot.topics` | List of topics shown to users |
| `spring.liquibase.change-log` | Liquibase master changelog path |

# Security Rules

## Secrets & Credentials — NEVER hardcode

**NEVER** put secrets, passwords, tokens, or keys directly in config files.
Always use environment variable placeholders instead.

### Required pattern for ALL credentials:
```yaml
# CORRECT
password: ${DB_PASSWORD}
api-key: ${API_KEY}
token: ${SECRET_TOKEN}

# NEVER DO THIS
password: myActualPassword123
api-key: sk-abc123...
```

### This applies to:
- Database passwords (`spring.datasource.password`)
- API keys and tokens (OpenAI, Azure, etc.)
- Bot tokens (Telegram, Slack, etc.)
- Any secret string

### When adding a new secret:
1. Use `${VAR_NAME}` placeholder in config
2. Add `VAR_NAME=` to `.env.example` (no value)
3. Add `VAR_NAME` to the list in `README.md` under "Environment Variables"
4. **Never** put the actual value anywhere except `.env` (which is in `.gitignore`)

### External Dependencies

- **Ollama** (local, `http://localhost:11434`): provides embeddings only (`nomic-embed-text`). Not used for generation.
- **External LLM API**: proprietary HTTP service; receives `{ query (base64), userId, url }`, returns `{ response, time }`. Called via `WebClient` configured in `RagConfig`.
- **Azure Blob Storage**: stores `vectorstore.bin`, `Performance-User-Guide.pdf`, `index-metadata.json`. Managed by `AzureBlobStorageService`.
- **PostgreSQL**: stores `BotFeedback` entities (thumbs-up/down feedback per conversation).

### Exception Handling

`GlobalExceptionHandler` (`@RestControllerAdvice`) handles:
- `CodedException` (base class) / `RestException` (concrete) → JSON `{ errorCode, message }`
- `MethodArgumentNotValidException` → validation error details
- Generic `Exception` → HTTP 500

`ErrorCodes` enum: `BOT_FEEDBACK_NOT_FOUND` (code 300000, status 404).

### Tests

Tests use the `test` Spring profile (`application-test.yml`) which:
- Substitutes H2 in-memory DB (`MODE=PostgreSQL`) for PostgreSQL
- Disables Liquibase (`spring.liquibase.enabled: false`) — schema created by `ddl-auto: create-drop`
- Disables Ollama embedding so `BootstrapIndexRunner` does not attempt to connect to Ollama or Azure Blob on startup

`AiHelperBotApplicationTests` mocks `EmbeddingModel` to verify the Spring context loads successfully.