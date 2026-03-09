# Plan: Migration to OpenRouter API

## Current State

The LLM integration uses a **proprietary API** that is incompatible with OpenRouter:

| Aspect | Current (proprietary) | Target (OpenRouter) |
|---|---|---|
| Endpoint | `POST {base-url}/ask` | `POST https://openrouter.ai/api/v1/chat/completions` |
| Auth header | `Api-Key: <key>` | `Authorization: Bearer <key>` |
| Request body | `{ query: base64(prompt), userId, url }` | `{ model, messages: [{role, content}] }` |
| Response body | `{ response, time }` | `{ id, choices: [{message: {role, content}}], usage }` |
| HTTP client | `WebClient` (reactive) + `.block()` | `RestClient` (synchronous, Spring Boot 3.2+) |
| Error handling | Errors swallowed, returned as plain strings | Exceptions + structured error response |

### Files to modify

| File | Action |
|---|---|
| `LlmProperties.java` | Rewrite: remove `userId`, `guideUrl`; add `model`, `temperature`, `maxTokens` |
| `RagConfig.java` | Rewrite: replace `WebClient` bean with `RestClient` bean |
| `LlmClient.java` | Rewrite: OpenRouter request/response DTOs, new `ask()` logic |
| `PromptBuilder.java` | Refactor: return structured `ChatMessage` list instead of single string |
| `RagService.java` | Adapt: use new `LlmClient` exception-based error handling |
| `application.yml` | Update: new property structure |
| `application-test.yml` | Update: match new property structure |
| `pom.xml` | Remove `spring-boot-starter-webflux`, `reactor-test` |

---

## Step 1. Update `LlmProperties`

Remove proprietary fields (`userId`, `guideUrl`, `mode`). Add OpenRouter-specific fields.

```java
package com.solbeg.sas.perfmgmnt.config.properties;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class LlmProperties {

    /** OpenRouter API base URL (default: https://openrouter.ai/api/v1) */
    @NotBlank
    private String baseUrl = "https://openrouter.ai/api/v1";

    /** OpenRouter API key */
    @NotBlank
    private String apiKey;

    /** Model identifier, e.g. "anthropic/claude-sonnet-4" */
    @NotBlank
    private String model;

    /** Sampling temperature (0.0 – 2.0). Lower = more deterministic. */
    private double temperature = 0.3;

    /** Maximum tokens in the response */
    private int maxTokens = 1024;

    /** Maximum number of documents to retrieve from vector store */
    private int maxDocuments = 5;

    /** Phrases in LLM responses that indicate an unsatisfactory answer */
    private List<String> unsatisfactoryPhrases = List.of(
            "not explicitly defined", "not specified");

    /**
     * Optional HTTP-Referer header sent to OpenRouter.
     * Used for attribution in the OpenRouter dashboard.
     */
    private String httpReferer = "";

    /**
     * Optional X-Title header sent to OpenRouter.
     * Used as app name in the OpenRouter dashboard.
     */
    private String appTitle = "AiHelperBot";
}
```

**Key decisions:**
- `guideUrl` was only used as LLM payload metadata — no longer needed with OpenRouter
- `userId` was proprietary — OpenRouter tracks usage by API key
- `model` is now explicit and configurable — essential for OpenRouter (mandatory field)
- `temperature` and `maxTokens` exposed as config — tunable without redeployment
- `httpReferer` and `appTitle` are OpenRouter-recommended headers for dashboard attribution

---

## Step 2. Update `application.yml`

```yaml
rag:
  llm:
    base-url: ${LLM_BASE_URL:https://openrouter.ai/api/v1}
    api-key: ${OPEN_ROUTER_API_KEY:}
    model: ${LLM_MODEL:}
    temperature: 0.3
    max-tokens: 1024
    max-documents: 5
    http-referer: ${LLM_HTTP_REFERER:}
    app-title: AiHelperBot
    unsatisfactory-phrases:
      - "not explicitly defined"
      - "not specified"
```

Remove: `user-id`, `guide-url`, `mode`.

Update `application-test.yml` similarly:
```yaml
rag:
  llm:
    base-url: http://localhost:9999
    api-key: test-key
    model: test/model
    max-documents: 3
```

---

## Step 3. Replace `WebClient` with `RestClient` in `RagConfig`

Remove `spring-boot-starter-webflux` dependency. Use `RestClient` (available since Spring Boot 3.2).

```java
package com.solbeg.sas.perfmgmnt.config;

import com.solbeg.sas.perfmgmnt.config.properties.RagProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
public class RagConfig {

    @Bean
    public RestClient llmRestClient(RagProperties properties) {
        var llm = properties.getLlm();
        var builder = RestClient.builder()
                .baseUrl(llm.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + llm.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        if (llm.getHttpReferer() != null && !llm.getHttpReferer().isBlank()) {
            builder.defaultHeader("HTTP-Referer", llm.getHttpReferer());
        }
        if (llm.getAppTitle() != null && !llm.getAppTitle().isBlank()) {
            builder.defaultHeader("X-Title", llm.getAppTitle());
        }

        return builder.build();
    }
}
```

**Why `RestClient` over `WebClient`:**
- The app uses Servlet stack (`spring-boot-starter-web`), not reactive
- Current code calls `.block()` anyway — no benefit from reactive
- `RestClient` is the idiomatic synchronous HTTP client in Spring Boot 3.2+
- Removes `spring-boot-starter-webflux` + `reactor-test` from dependencies

---

## Step 4. Refactor `PromptBuilder` — Return Structured Messages

OpenRouter uses the ChatML message format (`system` + `user` roles). Separate system prompt from user content.

```java
package com.solbeg.sas.perfmgmnt.service.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@Slf4j
public class PromptBuilder {

    // Existing SYSTEM_PROMPT text stays the same (the big text block)
    private static final String SYSTEM_PROMPT = """
        Answer the user's question using ONLY information from the provided text.
        ...same content as current...
        """;

    /**
     * Builds structured messages for the OpenRouter Chat Completions API.
     *
     * @param userQuestion the user's question
     * @param contextDocs  the list of context documents
     * @return ChatMessages containing system and user messages
     */
    public ChatMessages build(String userQuestion, List<Document> contextDocs) {
        StringBuilder context = new StringBuilder();
        for (Document document : contextDocs) {
            var page = document.getMetadata().getOrDefault("page", "N/A");
            context.append("Page ").append(page).append(":\n")
                   .append(document.getText()).append("\n\n");
        }

        String userContent = "User Question:\n" + userQuestion + "\n\n"
                + "Context (User Guide excerpts):\n" + context;

        log.debug("Built prompt: {} context docs, user content length: {}",
                contextDocs.size(), userContent.length());

        return new ChatMessages(SYSTEM_PROMPT, userContent);
    }

    /**
     * Structured holder for system + user messages.
     */
    public record ChatMessages(String systemMessage, String userMessage) {}
}
```

**Why split system/user:**
- OpenRouter (and all OpenAI-compatible APIs) treat `system` messages differently from `user` messages
- System messages set behavior; user messages provide the actual query+context
- Some models handle system instructions more reliably when they're in the dedicated `system` role
- Enables future per-model prompt tuning (some models prefer system instructions formatted differently)

---

## Step 5. Rewrite `LlmClient`

This is the core change. Replace the entire implementation.

### 5a. Create request/response DTOs

```java
package com.solbeg.sas.perfmgmnt.dto.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** OpenRouter Chat Completions request body. */
public record ChatCompletionRequest(
        String model,
        List<Message> messages,
        double temperature,
        @JsonProperty("max_tokens") int maxTokens
) {
    public record Message(String role, String content) {}
}
```

```java
package com.solbeg.sas.perfmgmnt.dto.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** OpenRouter Chat Completions response body. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatCompletionResponse(
        String id,
        List<Choice> choices,
        Usage usage
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(int index, Message message, String finishReason) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Message(String role, String content) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(int promptTokens, int completionTokens, int totalTokens) {}
}
```

### 5b. Create `LlmException`

```java
package com.solbeg.sas.perfmgmnt.exceptionhandler.exception;

/**
 * Thrown when the LLM API call fails (network error, HTTP error, empty response).
 * Allows callers to distinguish LLM failures from successful empty answers.
 */
public class LlmException extends RuntimeException {

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

### 5c. Rewrite `LlmClient`

```java
package com.solbeg.sas.perfmgmnt.service.rag;

import com.solbeg.sas.perfmgmnt.config.properties.RagProperties;
import com.solbeg.sas.perfmgmnt.dto.llm.ChatCompletionRequest;
import com.solbeg.sas.perfmgmnt.dto.llm.ChatCompletionRequest.Message;
import com.solbeg.sas.perfmgmnt.dto.llm.ChatCompletionResponse;
import com.solbeg.sas.perfmgmnt.exceptionhandler.exception.LlmException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Client for the OpenRouter Chat Completions API.
 *
 * <p>Sends structured chat messages (system + user) and extracts the assistant's
 * response text. Throws {@link LlmException} on any failure so the caller can
 * decide the user-facing error message.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LlmClient {

    private final RestClient llmRestClient;
    private final RagProperties properties;

    /**
     * Sends a chat completion request to OpenRouter.
     *
     * @param systemMessage the system prompt defining assistant behavior
     * @param userMessage   the user question + RAG context
     * @return the assistant's response text
     * @throws LlmException if the API call fails or returns an empty response
     */
    public String ask(String systemMessage, String userMessage) {
        var llm = properties.getLlm();

        var request = new ChatCompletionRequest(
                llm.getModel(),
                List.of(
                        new Message("system", systemMessage),
                        new Message("user", userMessage)
                ),
                llm.getTemperature(),
                llm.getMaxTokens()
        );

        try {
            ChatCompletionResponse response = llmRestClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ChatCompletionResponse.class);

            return extractContent(response);

        } catch (RestClientException e) {
            log.error("OpenRouter API call failed: {}", e.getMessage(), e);
            throw new LlmException("OpenRouter API call failed", e);
        }
    }

    private String extractContent(ChatCompletionResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            log.warn("Empty or null response from OpenRouter");
            throw new LlmException("OpenRouter returned empty response");
        }

        var choice = response.choices().get(0);
        if (choice.message() == null || choice.message().content() == null) {
            log.warn("OpenRouter choice has no message content");
            throw new LlmException("OpenRouter returned choice without content");
        }

        String content = choice.message().content().trim();
        if (content.isEmpty()) {
            throw new LlmException("OpenRouter returned blank content");
        }

        if (response.usage() != null) {
            log.info("OpenRouter response: model={}, tokens(prompt={}, completion={}, total={}), length={}",
                    response.id(),
                    response.usage().promptTokens(),
                    response.usage().completionTokens(),
                    response.usage().totalTokens(),
                    content.length());
        }

        return content;
    }
}
```

**Key design decisions:**
- **Throws `LlmException` instead of returning error strings.** This is the most important change. Callers can now distinguish errors from answers. `RagService` and `GlobalExceptionHandler` decide the user-facing message.
- **No Base64 encoding.** OpenRouter accepts plain JSON.
- **Uses `RestClient`** — synchronous, matches the servlet stack.
- **Logs token usage** — critical for cost monitoring with OpenRouter (you pay per token).

---

## Step 6. Adapt `RagService`

Update `generateAnswer()` to use the new `LlmClient.ask(system, user)` signature and catch `LlmException`.

```java
// Change in RagService.java

private String generateAnswer(String question, List<Document> documents) {
    PromptBuilder.ChatMessages messages = promptBuilder.build(question, documents);
    try {
        return llmClient.ask(messages.systemMessage(), messages.userMessage());
    } catch (LlmException e) {
        log.error("LLM call failed for question: {}", question, e);
        return ERROR_RESPONSE;
    }
}
```

Also update `retryWithFallbackStrategy` to handle `LlmException`:

```java
private String retryWithFallbackStrategy(String question) {
    List<Document> fallbackDocuments =
            documentRetriever.retrieve(
                    question, QueryType.GENERAL, properties.getLlm().getMaxDocuments());

    if (fallbackDocuments.isEmpty()) {
        return DEFAULT_RESPONSE;
    }

    try {
        return generateAnswer(question, fallbackDocuments);
    } catch (LlmException e) {
        log.error("LLM fallback call also failed", e);
        return ERROR_RESPONSE;
    }
}
```

Note: `generateAnswer` already catches `LlmException`, so the outer `retryWithFallbackStrategy` catch is technically redundant, but it makes the intent explicit and prevents `ERROR_RESPONSE` from being checked by `isAnswerUnsatisfactory`.

Better approach — move the try/catch to `answer()`:

```java
public String answer(String question) {
    // ... validation, query analysis, document retrieval ...

    try {
        String answer = generateAnswer(question, documents);

        if (isAnswerUnsatisfactory(answer) && queryType == QueryType.DEFINITION) {
            log.debug("Answer unsatisfactory for DEFINITION query, retrying with GENERAL");
            answer = retryWithFallbackStrategy(question);
        }

        return answer;
    } catch (LlmException e) {
        log.error("LLM call failed for question: {}", question, e);
        return ERROR_RESPONSE;
    }
}

// generateAnswer no longer catches — lets LlmException propagate
private String generateAnswer(String question, List<Document> documents) {
    PromptBuilder.ChatMessages messages = promptBuilder.build(question, documents);
    return llmClient.ask(messages.systemMessage(), messages.userMessage());
}
```

---

## Step 7. Update `pom.xml`

```xml
<!-- REMOVE these dependencies -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>

<!-- REMOVE from test scope -->
<dependency>
    <groupId>io.projectreactor</groupId>
    <artifactId>reactor-test</artifactId>
    <scope>test</scope>
</dependency>
```

No new dependencies needed — `RestClient` is part of `spring-boot-starter-web`.

---

## Step 8. Add `LlmException` to `GlobalExceptionHandler`

```java
@ExceptionHandler(LlmException.class)
public ResponseEntity<Map<String, Object>> handleLlmException(LlmException ex) {
    log.error("LLM service error: {}", ex.getMessage());
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("errorCode", "LLM_ERROR");
    body.put("message", "AI service temporarily unavailable");
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
}
```

This handler only fires if `LlmException` escapes `RagService` (which currently catches it). It's a safety net.

---

## Implementation Order

```
1. LlmProperties.java          — update fields
2. application.yml              — update config
3. application-test.yml         — update test config
4. pom.xml                      — remove webflux/reactor-test
5. RagConfig.java               — WebClient → RestClient
6. dto/llm/                     — create ChatCompletionRequest, ChatCompletionResponse
7. LlmException.java            — create exception class
8. PromptBuilder.java           — return ChatMessages record
9. LlmClient.java              — full rewrite
10. RagService.java              — adapt to new LlmClient signature + exception handling
11. GlobalExceptionHandler.java  — add LlmException handler
12. Tests                        — write tests for LlmClient, PromptBuilder, RagService
```

Steps 1-4 can be done in one commit (config + deps).
Steps 5-11 in a second commit (implementation).
Step 12 in a third commit (tests).

---

## Files Summary

| Action | File |
|---|---|
| **Modify** | `LlmProperties.java` |
| **Modify** | `application.yml` |
| **Modify** | `application-test.yml` |
| **Modify** | `pom.xml` |
| **Modify** | `RagConfig.java` |
| **Modify** | `PromptBuilder.java` |
| **Rewrite** | `LlmClient.java` |
| **Modify** | `RagService.java` |
| **Modify** | `GlobalExceptionHandler.java` |
| **Create** | `dto/llm/ChatCompletionRequest.java` |
| **Create** | `dto/llm/ChatCompletionResponse.java` |
| **Create** | `exceptionhandler/exception/LlmException.java` |
| **Delete** | `LlmClient.ExternalApiResponse` (inner record — removed with rewrite) |

---

## Risk & Rollback Notes

- **The `guideUrl` property is removed.** It was only passed in the proprietary payload. If other code references it (currently only `BootstrapIndexRunner` uses it for PDF hash — unrelated to LLM calls), `guideUrl` should be moved to `GuideProperties` instead.
  - Check: `BootstrapIndexRunner.java:140` passes `properties.getLlm().getGuideUrl()` to hash calculation. This must be moved to `properties.getGuide()` or kept as a separate property.
  - `PdfChunker.java:23` receives `guideUrl` as a parameter from `BootstrapIndexRunner` — used as document metadata `"source"`. Move to `GuideProperties`.
- **The `userId` property is removed.** Grep the codebase — it was only used in `LlmClient` payload.
- **OpenRouter returns markdown by default.** The current system prompt doesn't request plain text. If the frontend expects plain text, add `"Format your response as plain text without markdown."` to the system prompt.
- **Rate limits.** OpenRouter has per-model rate limits. Consider adding retry with exponential backoff in `LlmClient` for HTTP 429 responses.
