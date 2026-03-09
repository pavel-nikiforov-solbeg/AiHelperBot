package com.solbeg.sas.perfmgmnt.dto.llm;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Request body for the OpenRouter Chat Completions API.
 *
 * @param model       model identifier, e.g. {@code "anthropic/claude-sonnet-4"}
 * @param messages    ordered list of chat messages (system, user, assistant)
 * @param temperature sampling temperature (0.0–2.0)
 * @param maxTokens   maximum number of tokens to generate
 */
public record ChatCompletionRequest(
        String model,
        List<Message> messages,
        double temperature,
        @JsonProperty("max_tokens") int maxTokens
) {

    /**
     * A single chat message with a role and text content.
     *
     * @param role    one of {@code "system"}, {@code "user"}, {@code "assistant"}
     * @param content the message text
     */
    public record Message(String role, String content) {}
}
