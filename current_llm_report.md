# LLM Integration Report

## Overview

The application uses **two distinct AI services** that serve different roles:

| Service | Role | Technology |
|---|---|---|
| **Ollama** (local) | Embedding only — converts text to vectors | Spring AI `spring-ai-starter-model-ollama` |
| **External LLM API** | Text generation — produces the final answer | Custom HTTP client via WebClient |

---

## 1. External LLM API (Text Generation)

### `LlmClient` — `service/rag/LlmClient.java`

The sole class that calls the external LLM. It:
1. Base64-encodes the full prompt string (UTF-8).
2. POSTs JSON `{ query, userId, url }` to `/ask` on the configured base URL.
3. Sends the `Api-Key` header for authentication.
4. Deserializes the response into an inner record `ExternalApiResponse(String response, String time)` and returns `response`.

```java
Map<String, Object> payload = Map.of(
    "query",  Base64.encode(prompt),
    "userId", properties.getLlm().getUserId(),
    "url",    properties.getLlm().getGuideUrl()
);
// POST {base-url}/ask  Header: Api-Key: {apiKey}
```

### `RagConfig` — `config/RagConfig.java`

Creates the `WebClient` bean used by `LlmClient`:

```java
@Bean
public WebClient llmWebClient(RagProperties properties) {
    return WebClient.builder()
        .baseUrl(properties.getLlm().getBaseUrl())
        .defaultHeader("Api-Key", properties.getLlm().getApiKey())
        .build();
}
```

### `PromptBuilder` — `service/rag/PromptBuilder.java`

Assembles the string that `LlmClient` sends. The prompt has two parts:

**System prompt (hardcoded):** A detailed instruction block that tells the LLM:
- Answer using ONLY the provided context text.
- Domain-specific routing rules per query type (support, inbox, review, peers, growth plan, etc.).
- Fallback: if unrelated to the guide, respond `"Not specified in the User Guide"`.

**User section (dynamic):**
```
User Question:
<userQuestion>

Context (User Guide excerpts):
page N):
<chunk text>
...
```

Each context chunk includes the source PDF page number from document metadata.

---

## 2. Ollama (Embeddings)

### `CustomSimpleVectorStore` — `service/rag/CustomSimpleVectorStore.java`

Wraps Spring AI's `SimpleVectorStore`. At construction it receives an `EmbeddingModel` (provided by Spring AI's Ollama auto-configuration) and delegates all embedding computation to it. Ollama is called:
- During **index build** — when chunks are added via `store.add(docs)`.
- During **similarity search** — when `store.similaritySearch(request)` is called per user query.

### Spring AI Ollama auto-configuration

Spring AI auto-configures the `OllamaEmbeddingModel` bean from `application.yml`:

```yaml
spring:
  ai:
    ollama:
      base-url: http://localhost:11434
      embedding:
        enabled: true
        options:
          model: nomic-embed-text
      init:
        pull-model-strategy: never
```

The model `nomic-embed-text` must already be pulled in Ollama before the app starts (`pull-model-strategy: never` means the app will not attempt to pull it automatically).

---

## 3. Properties

### `LlmProperties` — `config/properties/LlmProperties.java`

Bound under prefix `solbeg.rag.llm`:

| Field | Type | Purpose |
|---|---|---|
| `baseUrl` | String | Base URL of the external LLM API |
| `apiKey` | String | Authentication key sent as `Api-Key` header |
| `userId` | String | User identifier included in every LLM request payload |
| `guideUrl` | String | URL of the online PDF guide, included in every LLM request payload |
| `vectorStorePath` | String | Local filesystem path for the serialized vector store |
| `mode` | String | Operating mode (currently always `"external"`) |
| `modelName` | String | LLM model name (optional; defaults to the service default) |
| `maxDocuments` | int | Maximum number of retrieved document chunks passed as context |

### `application.yml` values

```yaml
solbeg:
  rag:
    llm:
      base-url: ${LLM_BASE_URL:http://10.77.64.113:8081}   # env var
      api-key:  ${OPEN_ROUTER_API_KEY:}                             # env var, no default
      user-id:  ${LLM_USER_ID:chatbotai@solbeg.com}
      guide-url: ${LLM_GUIDE_URL:}                          # env var, no default
      vector-store-path: /tmp/sas-rag/vectorstore.bin
      mode: external
      model-name: ""
      max-documents: 5
```

Environment variables that must be set in production:

| Variable | Required | Description |
|---|---|---|
| `LLM_BASE_URL` | Yes (has fallback) | External LLM API base URL |
| `OPEN_ROUTER_API_KEY` | Yes (no default) | API key for authentication |
| `LLM_USER_ID` | No | Sender identity in requests |
| `LLM_GUIDE_URL` | Yes (no default) | URL sent in `url` field of LLM payload |

---

## 4. Data Flow (end to end)

```
User question (POST /api/v1/ask)
        │
        ▼
InputValidator          — rejects off-topic / non-English / too-short input
        │
        ▼
QueryAnalyzer           — classifies into QueryType (GENERAL, DEFINITION, SUPPORT, ...)
        │
        ▼
DocumentRetriever
  └─ EmbeddingIndexer.search()
       └─ CustomSimpleVectorStore.similaritySearch()
            └─ OllamaEmbeddingModel (nomic-embed-text)   ← Ollama called here
        │
        ▼
DocumentRanker          — re-ranks / merges candidates
        │
        ▼
PromptBuilder.build()   — system prompt + question + page-annotated chunks
        │
        ▼
LlmClient.ask()
  └─ POST {LLM_BASE_URL}/ask
       body: { query: base64(prompt), userId, url: guideUrl }
       header: Api-Key: {OPEN_ROUTER_API_KEY}
        │
        ▼
ExternalApiResponse.response   → returned to caller
```

---

## 5. Maven Dependencies

```xml
<!-- Ollama embeddings -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-ollama</artifactId>
    <version>1.1.2</version>
</dependency>

<!-- In-memory vector store (wraps EmbeddingModel) -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-vector-store</artifactId>
    <version>1.1.2</version>
</dependency>

<!-- WebClient (used by LlmClient) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```
