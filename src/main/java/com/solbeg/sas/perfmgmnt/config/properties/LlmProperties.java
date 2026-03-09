package com.solbeg.sas.perfmgmnt.config.properties;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Configuration properties for OpenRouter LLM service.
 * Contains connection details and model parameters.
 */
@Getter
@Setter
public class LlmProperties {

    /**
     * OpenRouter API base URL.
     */
    @NotBlank
    private String baseUrl = "https://openrouter.ai/api/v1";

    /**
     * OpenRouter API key (Bearer token).
     */
    @NotBlank
    private String apiKey;

    /**
     * Model identifier in OpenRouter format, e.g. {@code "anthropic/claude-sonnet-4"}.
     */
    @NotBlank
    private String model;

    /**
     * Sampling temperature (0.0–2.0). Lower values = more deterministic output.
     */
    private double temperature = 0.3;

    /**
     * Maximum number of tokens to generate in the response.
     */
    private int maxTokens = 1024;

    /**
     * Maximum number of documents to retrieve from vector store.
     * Used to limit context size in RAG queries.
     */
    private int maxDocuments = 5;

    /**
     * Optional HTTP-Referer header value sent to OpenRouter.
     * Used for attribution in the OpenRouter dashboard.
     */
    private String httpReferer = "";

    /**
     * Optional X-Title header value sent to OpenRouter.
     * Used as app name in the OpenRouter dashboard.
     */
    private String appTitle = "AiHelperBot";

    /**
     * Phrases in LLM responses that indicate an unsatisfactory answer.
     * Used by RagService to trigger a fallback retry with the GENERAL strategy.
     */
    private List<String> unsatisfactoryPhrases = List.of("not explicitly defined", "not specified");
}
