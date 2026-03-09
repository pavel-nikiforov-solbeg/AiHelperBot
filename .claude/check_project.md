# Project Review: AiHelperBot — Technical Issues, Inconsistencies & Oddities

**Date:** 2026-03-08
**Scope:** Full codebase review — architecture, RAG pipeline, configuration, security, tests

---

## 1. Critical Issues

### 1.1. LLM Integration is NOT OpenRouter — it's a proprietary Base64-encoded API

**Files:** `LlmClient.java:37-57`, `RagConfig.java:23-28`

The CLAUDE.md says "Access LLM via OpenRouter", but the actual implementation sends a **custom payload** `{ query (base64-encoded), userId, url }` to a proprietary endpoint `/ask`, and expects `{ response, time }` back. This is **not** the OpenRouter API format at all. OpenRouter uses the standard OpenAI-compatible `POST /api/v1/chat/completions` with `{ model, messages }`.

**Impact:** If the plan is to migrate to OpenRouter, the entire `LlmClient` needs to be rewritten. The Base64 encoding of the prompt is bizarre and serves no purpose (it's not encryption, it just inflates payload size by ~33%).

**Recommendation:** Either replace with actual OpenRouter integration (OpenAI-compatible SDK) or document why this proprietary API exists.

### 1.2. `@Async` Bootstrap with No Error Propagation to Health Check

**Files:** `BootstrapIndexRunner.java:37-47`, `RagService.java:44-47`

The `@Async @EventListener` means the vector store initializes in a background thread. If it fails, `RagService.answer()` returns `ERROR_RESPONSE` string forever — but there's **no health indicator**, no retry mechanism, and no way for an operator to know the system is degraded except by reading logs.

**Recommendation:** Register a custom `HealthIndicator` bean that reflects vector store status. Consider a startup retry loop with backoff rather than silent permanent degradation.

### 1.3. `@Async` Without Custom Executor

**File:** `AiHelperBotApplication.java:8`

`@EnableAsync` is declared but no custom `TaskExecutor` bean is defined. Spring defaults to `SimpleAsyncTaskExecutor`, which creates a **new thread per invocation** (no thread pool, no queue). For a one-time bootstrap this is fine, but it's a landmine if anyone adds more `@Async` methods later.

**Recommendation:** Define a `ThreadPoolTaskExecutor` bean with bounded pool/queue.

---

## 2. Architectural & Design Issues

### 2.1. Duplicate "Not specified" / Fallback Constants Across Classes

| Class | Constant | Value |
|---|---|---|
| `RagService` | `DEFAULT_RESPONSE` | `"Not specified in the User Guide"` |
| `LlmClient` | `FALLBACK_RESPONSE` | `"Not specified in the User Guide"` |
| `RagService` | `ERROR_RESPONSE` | `"AI Helper Bot temporarily unavailable..."` |
| `LlmClient` | `ERROR_RESPONSE` | `"Error: Unable to get response from remote API"` |

Two different classes define **the same fallback string** independently + two different error messages. The `RagService` caller never knows if `LlmClient` already returned a fallback, so the `isAnswerUnsatisfactory()` check could trigger a DEFINITION→GENERAL retry for what was actually a network error fallback.

**Recommendation:** Move response constants to a shared location. Make `LlmClient` throw exceptions instead of silently returning fallback strings — let `RagService` decide the user-facing message.

### 2.2. `LlmClient.ask()` Swallows Errors as User-Visible Strings

**File:** `LlmClient.java:63-72`

Errors from the LLM API are caught and returned as **plain text strings** (`"Error: Unable to get response from remote API"`) that look like a real answer to the caller. `RagService` has no way to distinguish a genuine answer from an error. This makes error handling, monitoring, and retry logic unreliable.

**Recommendation:** Throw a custom exception (e.g., `LlmException`) and let `RagService` or `GlobalExceptionHandler` decide the response.

### 2.3. `WebClient.block()` in a Servlet Stack

**File:** `LlmClient.java:52`

The project uses `spring-boot-starter-web` (Servlet/Tomcat) but imports `spring-boot-starter-webflux` solely for `WebClient`. Calling `.block()` on a `Mono` in a servlet thread is wasteful — you pay the overhead of reactive infrastructure without any benefit. It also makes the thread block anyway.

**Recommendation:** Use `RestClient` (available since Spring Boot 3.2) instead of `WebClient` for synchronous HTTP calls. Remove `spring-boot-starter-webflux` dependency.

### 2.4. `CustomSimpleVectorStore.rebuildIndexes()` — Similarity Search with Empty Query

**File:** `CustomSimpleVectorStore.java:113-118`

```java
delegate.similaritySearch(
    SearchRequest.builder()
        .query("")
        .topK(MAX_INDEX_SIZE)
        .similarityThreshold(0.0)
        .build());
```

An empty string `""` is embedded and compared via cosine similarity against all documents. This relies on the embedding model producing a meaningful vector for `""` and that `similarityThreshold(0.0)` catches everything. This is fragile — some embedding models return a zero vector for empty strings, which produces `NaN` cosine similarity.

**Recommendation:** `SimpleVectorStore` stores documents internally in a map. Access the internal store directly (via reflection or a proper delegate method) rather than abusing similarity search as "get all documents".

### 2.5. RAG Pipeline Returns Validation Errors as Regular Answers

**File:** `RagService.java:49-53`

When input validation fails (e.g., wrong language, off-topic), the rejection reason string like `"Please ask your question in English"` is returned as the **answer** in `LlmResponse`. The HTTP status is still 200 OK. The client has no way to distinguish a real answer from a validation rejection.

**Recommendation:** Return validation failures as HTTP 400/422 with a structured error body, or add a `type` field to `LlmResponse` (e.g., `answer`, `validation_error`, `error`).

### 2.6. `DocumentRetriever` is a God Method with 9 Strategy Branches

**File:** `DocumentRetriever.java:37-49`

Each `QueryType` has its own `retrieveFor*` method with hardcoded search query suffixes, hardcoded ranking queries, and inline filtering logic. Adding a new query type requires modifying this class. This violates OCP and SRP.

**Recommendation:** Extract a `RetrievalStrategy` interface with per-type implementations, selected via a `Map<QueryType, RetrievalStrategy>`. Or at minimum, move the hardcoded keyword strings to configuration.

### 2.7. Hardcoded Search Query Augmentation Strings

**Files:** `DocumentRetriever.java` (lines 72, 85, 100-102, 117-119, 151-152, 176-178)

Strings like `" support contact help error issue problem bug"` and `" select peers suggest peer choose peer..."` are hardcoded in Java code. These are essentially **prompt engineering** constants that should be tunable without recompilation.

**Recommendation:** Move to `application.yml` under `rag.guide.search-augmentation.*` or similar.

---

## 3. Configuration & Property Issues

### 3.1. YAML Indentation Makes `bot` and `rag` Top-Level — Not Under `solbeg`

**File:** `application.yml:22-25`

```yaml
bot:
  intro-text: "Hello!..."

rag:
  storage:
```

The CLAUDE.md says the prefix is `solbeg.rag` and `solbeg.bot`, but the actual YAML has `bot` and `rag` as **root-level keys**. The `@ConfigurationProperties(prefix = "rag")` in `RagProperties.java` confirms this — the prefix is `rag`, not `solbeg.rag`. The CLAUDE.md is wrong, or the config was refactored without updating docs.

### 3.2. `BotProperties` Missing `topics` Field

**File:** `BotProperties.java`

CLAUDE.md says `solbeg.bot.topics` should be a "List of topics shown to users", but `BotProperties` only has `introText`. The `BotTopicsResponse` record also only has `introText`. There are no topics at all — the endpoint `GET /api/v1/bot/topics` returns only intro text, not topics.

### 3.3. `BotFeedbackServiceImpl.save()` — Answer Truncation Mentioned in CLAUDE.md but Not Implemented

**File:** `BotFeedbackServiceImpl.java:24-31`

CLAUDE.md states: *"`BotFeedbackService` truncates answers to 10,000 chars before saving."* But the actual code does not truncate — it relies on `@Size(max = 10000)` validation in `BotFeedbackRequest`, which means oversized answers will cause a **400 error** rather than silent truncation. These are different behaviors.

### 3.4. `GuideProperties.filePath` is Declared but Never Used

**File:** `GuideProperties.java:17`

The `filePath` field exists in the properties class but is not referenced anywhere in the codebase. The PDF is always downloaded from Azure Blob Storage. Dead configuration property.

### 3.5. `LlmProperties.mode` Missing

**File:** `LlmProperties.java`

CLAUDE.md mentions `solbeg.rag.llm.mode` (`external` or `internal`), and `application.yml` has `mode: external`, but there is no `mode` field in `LlmProperties`. The property is silently ignored by Spring.

---

## 4. Logic & Correctness Issues

### 4.1. `BootstrapIndexRunner` — Reindex Check Runs Even After Successful Load

**File:** `BootstrapIndexRunner.java:80-97`

After `tryLoadStoreFromBlob()` succeeds (store loaded into memory), the code **still** calls `calculateCurrentConfigHash()` and `needsReindex()`. If the PDF has changed, it triggers a full rebuild even though the store was just loaded from blob. This could be intentional (to keep the index fresh), but it also means every startup that succeeds at loading from blob still downloads the PDF to compute a hash — unnecessarily.

**Recommendation:** Add an early return after `tryLoadStoreFromBlob()` if `indexer.isLoaded()` is true and reindex is not desired. Or make the reindex check explicit.

### 4.2. `searchCandidates` Double-Buffers

**File:** `DocumentRetriever.java:207-209`

```java
private List<Document> searchCandidates(String searchQuery, int maxDocuments) {
    List<Document> results = indexer.search(searchQuery, maxDocuments + CANDIDATE_BUFFER);
```

Callers already pass `maxDocuments + CANDIDATE_BUFFER`, then `searchCandidates` adds **another** `CANDIDATE_BUFFER`. So the actual topK is `maxDocuments + 2 * CANDIDATE_BUFFER` (e.g., 5 + 20 = 25 documents). This looks unintentional.

### 4.3. `QueryAnalyzer` — "time" Matches Everything with the Word "time"

**File:** `QueryAnalyzer.java:19`

```java
Pattern.compile("(?i)\\b(when|date|deadline|schedule|time|first)\\b")
```

The word `"time"` and `"first"` are extremely common in English. "What should I do for the first time?" or "Is this the first review?" would be classified as `TIME_RELATED`, which triggers time-specific search augmentation and ranking — likely degrading answer quality.

### 4.4. `INBOX_PATTERN` is Overly Broad

**File:** `QueryAnalyzer.java:25`

Matches `sent`, `approve`, `deny`, `comment`, `history`, `cancel`, `edit`, `open issue`, `remind`, `filter`. Many of these words are not inbox-specific. "Can I cancel a growth plan task?" would match `cancel` and be classified as `INBOX` instead of `GROWTH_PLAN`. The priority order (INBOX checked before GROWTH_PLAN) makes this worse.

### 4.5. `GROWTH_PLAN_PATTERN` Matches Bare `tasks?` — Far Too Broad

**File:** `QueryAnalyzer.java:37`

```java
Pattern.compile("(?i)\\b(growth plan|...tasks?\\b|progress|status|...)\\b")
```

Any question containing "task", "tasks", "progress", or "status" is classified as `GROWTH_PLAN`. E.g., "What is the progress of my review?" → wrongly classified.

### 4.6. `PdfChunker` May Produce Empty Chunks

**File:** `PdfChunker.java:58-59`

When the buffer overflows, `toDoc(content, ...)` is called with `buf.toString()` which could be only whitespace (from the overlap tail). No check for empty/blank content.

---

## 5. Security Issues

### 5.1. CSRF Disabled + No Authentication = Wide Open

**File:** `SecurityConfig.java:13-17`

All endpoints permit all requests. CSRF is disabled. There is no rate limiting. The `/api/v1/ask` endpoint calls the LLM API on every request — this is a **cost amplification attack vector**. An attacker can send thousands of requests, each triggering an LLM API call.

### 5.2. API Key in WebClient Default Header — Logged in Debug/Trace

**File:** `RagConfig.java:27`

The LLM API key is set as a default header on the WebClient. Spring WebFlux's `ExchangeFilterFunction` and Netty logging at DEBUG level will log all headers including `Api-Key`. If log level is ever increased for troubleshooting, the key leaks to logs.

### 5.3. `employee_email` Column Not Useful Without Authentication

**File:** `BotFeedbackServiceImpl.java:27`

`SecurityUtils.getUserName()` always returns `null` because `SecurityConfig` permits all requests anonymously. The `employee_email` column in `bot_feedback` is always null. This is dead code.

---

## 6. Performance Issues

### 6.1. `DocumentRanker.rankByKeywords()` Logs Every Document at INFO

**File:** `DocumentRanker.java:54-55`

```java
log.info("Ranked by keywords size: {}", ranked.size());
```

This runs on every user request. With `log.debug` inside the loop (lines 64-69) it's fine, but the `log.info` on line 54 adds noise to production logs. More importantly, `calculateKeywordScore` is called **twice** per document — once in the sort comparator and once in the logging loop.

### 6.2. `Lingua LanguageDetector` Loads ALL Language Models

**File:** `LinguaConfig.java:31`

`LanguageDetectorBuilder.fromAllLanguages()` loads ~75 language models into memory. This adds significant startup time and ~200-500MB heap. If only English needs to be *accepted*, consider `fromLanguages(Language.ENGLISH, Language.GERMAN, ...)` with a small set of common confusing languages.

### 6.3. `BotFeedback.id` Missing Access Modifier

**File:** `BotFeedback.java:30`

```java
Long id;  // package-private, not private
```

All other fields are `private`. This is likely a typo but it breaks encapsulation for the primary key.

---

## 7. Test Coverage Issues

### 7.1. Only One Test — Context Load

**File:** `AiHelperBotApplicationTests.java`

The entire project has **a single test** that only verifies the Spring context loads. Zero tests for:
- RAG pipeline (`RagService`, `QueryAnalyzer`, `DocumentRetriever`, `DocumentRanker`)
- Input validation filters
- `LlmClient` error handling
- `BotFeedbackService` CRUD
- `BootstrapIndexRunner` initialization flow
- `PdfChunker` chunking logic

### 7.2. `@MockBean` Deprecation

**File:** `AiHelperBotApplicationTests.java:16`

`@MockBean` from `spring-boot-test` is deprecated in Spring Boot 3.4+. Use `@MockitoBean` instead.

### 7.3. Testcontainers Dependency Unused

**File:** `pom.xml:161-169`

`testcontainers` and `testcontainers-postgresql` are declared but never used. Tests use H2 instead.

---

## 8. Dependency Issues

### 8.1. `reactor-test` Dependency Not Needed

**File:** `pom.xml:171-174`

`reactor-test` is a test dependency for reactive streams, but the project has no reactive tests (and should probably remove WebFlux entirely — see 2.3).

### 8.2. Spring AI Version Not Managed by BOM

**File:** `pom.xml:91-102`

`spring-ai-starter-model-ollama` and `spring-ai-vector-store` use hardcoded version `1.1.2`. Spring AI has a BOM (`spring-ai-bom`) that should be used in `<dependencyManagement>` to ensure version alignment.

### 8.3. PDFBox 2.x is Legacy

**File:** `pom.xml:108`

PDFBox 2.0.29 is the legacy branch. PDFBox 3.x has been stable since 2023. The `PDDocument.load(InputStream)` API used in `PdfChunker` was removed in PDFBox 3.x (replaced with `Loader.loadPDF()`).

---

## 9. Code Style & Minor Oddities

### 9.1. Inconsistent `@Component` vs `@Service` Usage

| Class | Annotation | Role |
|---|---|---|
| `LlmClient` | `@Component` | Acts as a service |
| `AzureBlobStorageService` | `@Service` | Service |
| `DocumentRetriever` | `@Component` | Acts as a service |
| `DocumentRanker` | `@Component` | Acts as a service |
| `EmbeddingIndexer` | `@Component` | Acts as a service |

Several classes that encapsulate business logic use `@Component` instead of `@Service`. This is functionally equivalent but inconsistent with Spring conventions.

### 9.2. `PromptBuilder` — Unused Index Variable

**File:** `PromptBuilder.java:54`

```java
for (int i = 0; i < contextDocs.size(); i++) {
    var document = contextDocs.get(i);
```

The index `i` is never used (not printed in the context). Could be a for-each loop.

### 9.3. `BotTopicsResponse` — Misleadingly Named

**File:** `BotTopicsResponse.java`

The response record and endpoint are called "topics" but only return `introText`. There is no topics list.

### 9.4. `DocumentRanker` — Three Identical `mergeWith*Page` Methods

**File:** `DocumentRanker.java:108-154`

`mergeWithGlossaryPage`, `mergeWithSupportPage`, and `mergeWithInboxPage` are **identical** except for parameter names and log messages. This is textbook DRY violation.

```java
// All three do exactly the same thing:
List<Document> pageDocs = indexer.findByPageNumber(pageNumber);
return Stream.concat(pageDocs.stream(), documents.stream()).distinct().limit(maxDocuments).toList();
```

### 9.5. `CodedException.httpStatus` is `int` but `ErrorCodes.httpStatus` is `HttpStatus`

**Files:** `CodedException.java:16`, `ErrorCodes.java:15`

`CodedException` stores the HTTP status as a raw `int`, but it's constructed from `ErrorCodes` which has `HttpStatus`. The handler in `GlobalExceptionHandler` calls `ex.getHttpStatus()` and passes the `int` to `ResponseEntity.status(int)`. This works but is type-unsafe — a typo like `999` would produce an invalid HTTP response.

---

## Summary

| Severity | Count | Key Areas |
|---|---|---|
| **Critical** | 3 | LLM integration mismatch, no health indicator, no thread pool |
| **Architectural** | 7 | Error swallowing, God method, hardcoded prompts, WebClient misuse |
| **Configuration** | 5 | YAML/doc mismatch, dead properties, missing truncation |
| **Logic** | 6 | Overly broad regex, double-buffering, empty chunks |
| **Security** | 3 | No auth, API key exposure risk, dead email field |
| **Performance** | 3 | Double score calculation, Lingua memory, INFO noise |
| **Testing** | 3 | Almost zero coverage, unused deps, deprecated API |
| **Dependencies** | 3 | No BOM, legacy PDFBox, unused reactive deps |
| **Style** | 5 | DRY violations, naming inconsistencies |

**Overall assessment:** The RAG pipeline works but has significant technical debt. The most pressing issues are: (1) the LLM integration does not match claimed OpenRouter usage, (2) errors are silently swallowed as user-visible strings making debugging impossible, (3) the regex-based query classification is brittle and overly broad, and (4) there is virtually no test coverage.
