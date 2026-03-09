package com.solbeg.sas.perfmgmnt.service.rag;

import com.solbeg.sas.perfmgmnt.config.properties.LlmProperties;
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
 * <p>Sends structured chat messages ({@code system} + {@code user} roles) to
 * {@code POST /chat/completions} and extracts the assistant's response text.
 *
 * <p>Throws {@link LlmException} on any failure (network error, HTTP error, empty response)
 * so callers can decide the appropriate user-facing message rather than receiving a silent
 * fallback string.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LlmClient {

    private final RestClient llmRestClient;
    private final RagProperties properties;

    /**
     * Sends a chat completion request to OpenRouter and returns the generated text.
     *
     * @param systemMessage the system prompt that defines assistant behavior and constraints
     * @param userMessage   the user question combined with RAG context documents
     * @return the assistant's response text (trimmed, never blank)
     * @throws LlmException if the API call fails or returns an empty/null response
     */
    public String ask(String systemMessage, String userMessage) {
        LlmProperties llm = properties.getLlm();

        ChatCompletionRequest request = new ChatCompletionRequest(
                llm.getModel(),
                List.of(
                        new Message("system", systemMessage),
                        new Message("user", userMessage)
                ),
                llm.getTemperature(),
                llm.getMaxTokens()
        );

        log.debug("Sending request to OpenRouter: model={}, maxTokens={}", llm.getModel(), llm.getMaxTokens());

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

        ChatCompletionResponse.Choice choice = response.choices().get(0);
        if (choice.message() == null || choice.message().content() == null) {
            log.warn("OpenRouter choice has no message content");
            throw new LlmException("OpenRouter returned choice without content");
        }

        String content = choice.message().content().trim();
        if (content.isEmpty()) {
            throw new LlmException("OpenRouter returned blank content");
        }

        if (response.usage() != null) {
            log.info("OpenRouter response received: id={}, tokens=(prompt={}, completion={}, total={}), length={}",
                    response.id(),
                    response.usage().promptTokens(),
                    response.usage().completionTokens(),
                    response.usage().totalTokens(),
                    content.length());
        } else {
            log.info("OpenRouter response received: id={}, length={}", response.id(), content.length());
        }

        return content;
    }
}
